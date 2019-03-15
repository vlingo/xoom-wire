// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.channel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

import io.vlingo.actors.Actor;
import io.vlingo.actors.Stoppable;
import io.vlingo.common.Cancellable;
import io.vlingo.common.Scheduled;
import io.vlingo.wire.message.BasicConsumerByteBuffer;
import io.vlingo.wire.message.ConsumerByteBuffer;

public class SocketChannelSelectionProcessorActor extends Actor
    implements SocketChannelSelectionProcessor, ResponseSenderChannel, Scheduled<Object>, Stoppable {

  private int bufferId;
  private final Cancellable cancellable;
  private int contextId;
  private final int messageBufferSize;
  //private final int maxBufferPoolSize;
  private final String name;
  private final RequestChannelConsumerProvider provider;
  private final ResponseSenderChannel responder;
  private final Selector selector;

  @SuppressWarnings("unchecked")
  public SocketChannelSelectionProcessorActor(
          final RequestChannelConsumerProvider provider,
          final String name,
          final int maxBufferPoolSize,
          final int messageBufferSize,
          final long probeInterval) {

    this.provider = provider;
    this.name = name;
    this.messageBufferSize = messageBufferSize;
    this.selector = open();
    //this.maxBufferPoolSize = maxBufferPoolSize;
    this.responder = selfAs(ResponseSenderChannel.class);

    this.cancellable = stage().scheduler().schedule(selfAs(Scheduled.class), null, 100, probeInterval);
  }


  //=========================================
  // ResponseSenderChannel
  //=========================================

  @Override
  public void abandon(final RequestResponseContext<?> context) {
    ((Context) context).close();
  }

  @Override
  public void close() {
    if (isStopped()) return;

    selfAs(Stoppable.class).stop();
  }

  @Override
  public void respondWith(final RequestResponseContext<?> context, final ConsumerByteBuffer buffer) {
    ((Context) context).queueWritable(buffer);
  }


  //=========================================
  // SocketChannelSelectionProcessor
  //=========================================

  @Override
  public void process(final SelectionKey key) {
    try {
      final ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();

      if (serverChannel.isOpen()) {
        final SocketChannel clientChannel = serverChannel.accept();

        if (clientChannel != null) {
          clientChannel.configureBlocking(false);

          clientChannel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE, new Context(clientChannel));
        }
      }
    } catch (Exception e) {
      final String message = "Failed to accept client socket for " + name + " because: " + e.getMessage();
      logger().log(message, e);
      throw new IllegalArgumentException(message);
    }
  }


  //=========================================
  // Scheduled
  //=========================================

  @Override
  public void intervalSignal(final Scheduled<Object> scheduled, final Object data) {
    probeChannel();
  }


  //=========================================
  // Stoppable
  //=========================================

  @Override
  public void stop() {
    cancellable.cancel();

    try {
      selector.close();
    } catch (Exception e) {
      logger().log("Failed to close selctor for " + name + " while stopping because: " + e.getMessage(), e);
    }
  }


  //=========================================
  // internal implementation
  //=========================================

  private Selector open() {
    try {
      return Selector.open();
    } catch (Exception e) {
      final String message = "Failed to open selector for " + name + " because: " + e.getMessage();
      logger().log(message, e);
      throw new IllegalArgumentException(message);
    }
  }

  private void probeChannel() {
    if (isStopped()) return;

    try {
      if (selector.selectNow() > 0) {
        final Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();

        while (iterator.hasNext()) {
          final SelectionKey key = iterator.next();
          iterator.remove();

          if (key.isValid()) {
            if (key.isReadable()) {
              read(key);
            } else if (key.isWritable()) {
              write(key);
            }
          }
        }
      }
    } catch (Exception e) {
      logger().log("Failed client channel processing for " + name + " because: " + e.getMessage(), e);
    }
  }

  private void read(final SelectionKey key) throws IOException {
    final SocketChannel channel = (SocketChannel) key.channel();

    if (!channel.isOpen()) {
      key.cancel();
      return;
    }

    final Context context = (Context) key.attachment();
    final ConsumerByteBuffer buffer = context.requestBuffer().clear();
    final ByteBuffer readBuffer = buffer.asByteBuffer();

    int totalBytesRead = 0;
    int bytesRead = 0;

    try {
      do {
        bytesRead = channel.read(readBuffer);
        totalBytesRead += bytesRead;
      } while (bytesRead > 0);
    } catch (Exception e) {
      // likely a forcible close by the client,
      // so force close and cleanup
      bytesRead = -1;
    }

    if (bytesRead == -1) {
      channel.close();
      key.cancel();
    }

    if (totalBytesRead > 0) {
      context.consumer().consume(context, buffer.flip());
    } else {
      buffer.release();
    }
  }

  private void write(final SelectionKey key) throws Exception {
    final SocketChannel channel = (SocketChannel) key.channel();

    if (!channel.isOpen()) {
      key.cancel();
      return;
    }

    final Context context = (Context) key.attachment();

    if (context.hasNextWritable()) {
      writeWithCachedData(context, channel);
    }
  }

  private void writeWithCachedData(final Context context, final SocketChannel channel) throws Exception {
    for (ConsumerByteBuffer buffer = context.nextWritable() ; buffer != null; buffer = context.nextWritable()) {
      writeWithCachedData(context, channel, buffer);
    }
  }

  private void writeWithCachedData(final Context context, final SocketChannel clientChannel, ConsumerByteBuffer buffer) throws Exception {
    try {
      final ByteBuffer responseBuffer = buffer.asByteBuffer();
      while (responseBuffer.hasRemaining()) {
        clientChannel.write(responseBuffer);
      }
    } catch (Exception e) {
      logger().log("Failed to write buffer for " + name + " with channel " + clientChannel.getRemoteAddress() + " because: " + e.getMessage(), e);
    } finally {
      buffer.release();
    }
  }


  //=========================================
  // internal implementation
  //=========================================

  private class Context implements RequestResponseContext<SocketChannel> {
    private final ConsumerByteBuffer buffer;
    private final SocketChannel clientChannel;
    private Object closingData;
    private final RequestChannelConsumer consumer;
    private Object consumerData;
    private final String id;
    private final Queue<ConsumerByteBuffer> writables;

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
    public ResponseSenderChannel sender() {
      return responder;
    }

    @Override
    public void whenClosing(final Object data) {
      this.closingData = data;
    }

    Context(final SocketChannel clientChannel) {
      this.clientChannel = clientChannel;
      this.consumer = provider.requestChannelConsumer();
      this.buffer = BasicConsumerByteBuffer.allocate(++bufferId, messageBufferSize);
      this.id = "" + (++contextId);
      this.writables = new LinkedList<>();
    }

    void close() {
      if (!clientChannel.isOpen()) return;

      try {
        consumer().closeWith(this, closingData);
        clientChannel.close();
      } catch (Exception e) {
        logger().log("Failed to close client channel for " + name + " because: " + e.getMessage(), e);
      }
    }

    RequestChannelConsumer consumer() {
      return consumer;
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
      return buffer;
    }
  }
}
