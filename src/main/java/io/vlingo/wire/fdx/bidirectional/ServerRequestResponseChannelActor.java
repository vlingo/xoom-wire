// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.fdx.bidirectional;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

import io.vlingo.actors.Actor;
import io.vlingo.actors.Cancellable;
import io.vlingo.actors.Scheduled;
import io.vlingo.wire.channel.RequestChannelConsumer;
import io.vlingo.wire.channel.RequestResponseContext;
import io.vlingo.wire.channel.ResponseSenderChannel;
import io.vlingo.wire.message.ByteBufferPool;
import io.vlingo.wire.message.ConsumerByteBuffer;

public class ServerRequestResponseChannelActor extends Actor implements ServerRequestResponseChannel, Scheduled {
  private static long NextContextId = 1;

  private final Cancellable cancellable;
  private final ServerSocketChannel channel;
  private boolean closed;
  private RequestChannelConsumer consumer;
  private final int maxBufferPoolSize;
  private final int maxMessageSize;
  private final String name;
  private final long probeTimeout;
  private final Selector selector;
  private final ResponseSenderChannel self;

  public ServerRequestResponseChannelActor(
          final RequestChannelConsumer consumer,
          final int port,
          final String name,
          final int maxBufferPoolSize,
          final int maxMessageSize,
          final long probeTimeout,
          final long probeInterval)
  throws Exception {
    this.consumer = consumer;
    this.name = name;
    this.maxBufferPoolSize = maxBufferPoolSize;
    this.maxMessageSize = maxMessageSize;
    this.probeTimeout = probeTimeout;
    
    this.self = selfAs(ResponseSenderChannel.class);
    this.channel = ServerSocketChannel.open();
    this.selector = Selector.open();
    channel.socket().bind(new InetSocketAddress(port));
    channel.configureBlocking(false);
    channel.register(selector, SelectionKey.OP_ACCEPT);

    this.cancellable = stage().scheduler().schedule(selfAs(Scheduled.class), null, 100, probeInterval);
  }


  //=========================================
  // Scheduled
  //=========================================

  @Override
  public void intervalSignal(final Scheduled scheduled, final Object data) {
    probeChannel();
  }


  //=========================================
  // Stoppable
  //=========================================

  @Override
  public void stop() {
    cancellable.cancel();

    close();

    super.stop();
  }


  //=========================================
  // ResponseSenderChannel
  //=========================================

  @Override
  public void abandon(final RequestResponseContext<?> context) {
    // TODO: determine if this is still needed
    ((Context) context).close();
  }

  @Override
  public void close() {
    if (closed) return;

    closed = true;

    try {
      selector.close();
    } catch (Exception e) {
      logger().log("Failed to close selctor for: '" + name + "'", e);
    }

    try {
      channel.close();
    } catch (Exception e) {
      logger().log("Failed to close channel for: '" + name + "'", e);
    }
  }

  @Override
  public void respondWith(final RequestResponseContext<?> context, final ConsumerByteBuffer buffer) {
    ((Context) context).queueWritable(buffer);
  }


  //=========================================
  // internal implementation
  //=========================================

  private void accept(final SelectionKey key) throws IOException {
    final ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();

    if (serverChannel.isOpen()) {
      final SocketChannel clientChannel = serverChannel.accept();
  
      clientChannel.configureBlocking(false);
  
      clientChannel.register(
              selector,
              SelectionKey.OP_READ | SelectionKey.OP_WRITE,
              new Context(NextContextId++, self, clientChannel));

      logger().log(
              "Accepted new connection for '"
              + name
              + "' from: "
              + clientChannel.getRemoteAddress());
    }
  }

  private void probeChannel() {
    if (closed) return;

    try {
      if (selector.select(probeTimeout) > 0) {
        final Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();

        while (iterator.hasNext()) {
          final SelectionKey key = iterator.next();
          iterator.remove();

          if (key.isValid()) {
            if (key.isAcceptable()) {
              accept(key);
            } else if (key.isReadable()) {
              read(key);
            } else if (key.isWritable()) {
              write(key);
            }
          }
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
      logger().log("Failed to accept/read/write/close client channel for '" + name + "' because: " + e.getMessage(), e);
    }
  }

  private void read(final SelectionKey key) throws IOException {
    final SocketChannel channel = (SocketChannel) key.channel();
    final Context context = (Context) key.attachment();
    final ConsumerByteBuffer buffer = context.requestBuffer().clear();
    final ByteBuffer readBuffer = buffer.asByteBuffer();

    int totalBytesRead = 0;
    int bytesRead = 0;
    do {
      bytesRead = channel.read(readBuffer);
      totalBytesRead += bytesRead;
    } while (bytesRead > 0);

    if (bytesRead == -1) {
      context.close();
      key.cancel();
    }

    if (totalBytesRead > 0) {
      consumer.consume(context, buffer.flip());
    } else {
      buffer.release();
    }
  }

  private void respondWithCachedData(final Context context) throws Exception {
    final SocketChannel clientChannel = context.reference();

    for (ConsumerByteBuffer buffer = context.nextWritable() ; buffer != null; buffer = context.nextWritable()) {
      respondWithCachedData(context, clientChannel, buffer);
    }
  }

  private void respondWithCachedData(final Context context, final SocketChannel clientChannel, ConsumerByteBuffer buffer) throws Exception {
    try {
      final ByteBuffer responseBuffer = buffer.asByteBuffer();
      while (responseBuffer.hasRemaining()) {
        clientChannel.write(responseBuffer);
      }
    } catch (Exception e) {
      logger().log("Failed to write buffer for channel " + clientChannel.getRemoteAddress() + " because: " + e.getMessage(), e);
    } finally {
      buffer.release();
    }
  }

  private void write(final SelectionKey key) throws Exception {
    final Context context = (Context) key.attachment();
    if (context.hasNextWritable()) {
      respondWithCachedData(context);
    }
  }

  //=========================================
  // Context (RequestResponseContext)
  //=========================================

  class Context implements RequestResponseContext<SocketChannel> {
    private final ByteBufferPool bufferPool;
    private final SocketChannel clientChannel;
    private Object consumerData;
    private final String id;
    private final Queue<ConsumerByteBuffer> writables;
    private final ResponseSenderChannel responder;

    @Override
    @SuppressWarnings("unchecked")
    public <T> T consumerData() {
      return (T) consumerData;
    }

    @Override
    public <T> T consumerData(final T workingData) {
      this.consumerData = workingData;
      return workingData;
    }

    @Override
    public boolean hasConsumerData() {
      return consumerData != null;
    }

    @Override
    public String id() {
      return id;
    }

    @Override
    public SocketChannel reference() {
      return clientChannel;
    }

    @Override
    public ResponseSenderChannel sender() {
      return responder;
    }

    Context(final long id, final ResponseSenderChannel responder, final SocketChannel clientChannel) {
      this.responder = responder;
      this.clientChannel = clientChannel;
      this.bufferPool = new ByteBufferPool(maxBufferPoolSize, maxMessageSize);
      this.consumerData = null;
      this.id = "" + id;
      this.writables = new LinkedList<>();
    }

    void close() {
      try {
        clientChannel.close();
      } catch (Exception e) {
        e.printStackTrace();
        logger().log("Failed to close client channel because: " + e.getMessage(), e);
      }
    }

    boolean hasNextWritable() {
      return writables.peek() != null;
    }

    ConsumerByteBuffer nextWritable() {
      return writables.poll();
    }

    void queueWritable(final ConsumerByteBuffer buffer) {
      writables.add(buffer);
    }

    ConsumerByteBuffer requestBuffer() {
      final ConsumerByteBuffer buffer = bufferPool.accessFor("request", 25);
      return buffer;
    }
  }
}
