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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import io.vlingo.actors.Logger;
import io.vlingo.wire.channel.RequestChannelConsumer;
import io.vlingo.wire.channel.RequestListenerChannel;
import io.vlingo.wire.channel.RequestResponseContext;
import io.vlingo.wire.channel.ResponseData;
import io.vlingo.wire.channel.ResponseSenderChannel;
import io.vlingo.wire.message.ByteBufferPool;
import io.vlingo.wire.message.ByteBufferPool.PooledByteBuffer;
import io.vlingo.wire.message.ConsumerByteBuffer;

public class ServerRequestResponseChannel implements RequestListenerChannel, ResponseSenderChannel {
  private final ByteBufferPool bufferPool;
  private final ServerSocketChannel channel;
  private boolean closed;
  private RequestChannelConsumer consumer;
  private final Logger logger;
  private final String name;
  private final int port;
  private final long probeTimeout;
  private final Selector selector;

  public ServerRequestResponseChannel(
          final int port,
          final String name,
          final int maxBufferPoolSize,
          final int maxMessageSize,
          final long probeTimeout,
          final Logger logger)
  throws Exception {
    this.port = port;
    this.name = name;
    this.channel = ServerSocketChannel.open();
    this.probeTimeout = probeTimeout;
    this.logger = logger;
    this.bufferPool = allocateBufferPool(maxBufferPoolSize, maxMessageSize);
    this.selector = Selector.open();
  }

  //=========================================
  // RequestListenerChannel
  //=========================================

  @Override
  public void close() {
    if (closed) return;
    
    closed = true;
    
    try {
      selector.close();
    } catch (Exception e) {
      logger.log("Failed to close selctor for: '" + name + "'", e);
    }
    
    try {
      channel.close();
    } catch (Exception e) {
      logger.log("Failed to close channel for: '" + name + "'", e);
    }
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public void openFor(final RequestChannelConsumer consumer) throws IOException {
    if (closed) return; // for some tests it's possible to receive close() before start()

    this.consumer = consumer;

    channel.socket().bind(new InetSocketAddress(port));
    channel.configureBlocking(false);
    channel.register(selector, SelectionKey.OP_ACCEPT);
  }

  @Override
  public void probeChannel() {
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
      logger.log("Failed to accept/read/write/close client channel for '" + name + "' because: " + e.getMessage(), e);
    }
  }


  //=========================================
  // ResponseSenderChannel
  //=========================================

  @Override
  public void abandon(final RequestResponseContext<?> context) {
    ((Context) context).close();
  }

  @Override
  public void respondOnceWith(final RequestResponseContext<?> context) {
    respondWith(context, true);
  }

  @Override
  public void respondWith(RequestResponseContext<?> context, boolean completes) {
    try {
      final Context localContext = (Context) context;
      if (completes) localContext.closable();
      final SocketChannel clientChannel = localContext.reference();
      if (localContext.firstResponse()) clientChannel.register(selector, SelectionKey.OP_WRITE, context);
    } catch (Exception e) {
      logger.log("Failed to stage response because: " + e.getMessage(), e);
    }
  }

  //=========================================
  // internal implementation
  //=========================================

  private void accept(final SelectionKey key) throws IOException {
    final ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();

    if (serverChannel.isOpen()) {
      final SocketChannel clientChannel = serverChannel.accept();
  
      clientChannel.configureBlocking(false);
  
      clientChannel.register(selector, SelectionKey.OP_READ, new Context(this, clientChannel));

      logger.log(
              "Accepted new connection for '"
              + name
              + "' from: "
              + clientChannel.getRemoteAddress());
    }
  }

  private ByteBufferPool allocateBufferPool(final int maxBufferPoolSize, final int maxMessageSize) {
    // buffers tend to be used in pairs, one for request and one for response
    final int actualPoolSize = (maxBufferPoolSize % 2 == 0 ? maxBufferPoolSize : maxBufferPoolSize + 1);
    return new ByteBufferPool(actualPoolSize, maxMessageSize);
  }

  private void read(final SelectionKey key) throws IOException {
    final SocketChannel channel = (SocketChannel) key.channel();
    final Context context = (Context) key.attachment();
    final ByteBuffer buffer = context.requestBuffer().asByteBuffer();

    int totalBytesRead = 0;
    int bytesRead = 0;
    do {
      bytesRead = channel.read(buffer);
      totalBytesRead += bytesRead;
    } while (bytesRead > 0);

    if (totalBytesRead > 0 && buffer.limit() > 0) {
      buffer.flip();
      consumer.consume(context);
      buffer.clear();
    }
  }

  private void respondWith(final Context context) throws Exception {
    final SocketChannel clientChannel = context.reference();
    final ResponseData responseData = context.nextResponseData();
    final ByteBuffer responseBuffer = responseData.buffer.asByteBuffer();
    
    try {
      while (responseBuffer.hasRemaining()) {
        clientChannel.write(responseBuffer);
      }
    } catch (Exception e) {
      logger.log("Failed to write buffer for channel " + clientChannel.getRemoteAddress() + " because: " + e.getMessage(), e);
    } finally {
      context.release(responseData);
      context.close();
    }
  }

  private void write(final SelectionKey key) throws Exception {
    final Context context = (Context) key.attachment();
    respondWith(context);
  }

  class Context implements RequestResponseContext<SocketChannel> {
    private final SocketChannel clientChannel;
    private boolean closable;
    private final List<ResponseData> orderedResponseData;
    final PooledByteBuffer requestBuffer;
    private final ResponseSenderChannel responder;

    Context(final ResponseSenderChannel responder, final SocketChannel clientChannel) {
      this.responder = responder;
      this.clientChannel = clientChannel;
      this.requestBuffer = bufferPool.access();
      this.closable = false;
      this.orderedResponseData = new ArrayList<>(1);
    }

    void close() {
      if (!closable || !orderedResponseData.isEmpty()) return;
      
      try {
        requestBuffer.release();
        
        for (final ResponseData responseData : orderedResponseData) {
          ((PooledByteBuffer) responseData.buffer).release();
        }
        
        //clientChannel.close();
        
      } catch (Exception e) {
        logger.log("Failed to clsoe client channel because: " + e.getMessage(), e);
      }
    }

    void closable() {
      closable = true;
    }

    boolean firstResponse() {
      return orderedResponseData.size() == 1;
    }

    ResponseData nextResponseData() {
      final ResponseData responseData = orderedResponseData.remove(0);
      return responseData;
    }

    void release(final ResponseData responseData) {
      orderedResponseData.remove(responseData);
      ((PooledByteBuffer) responseData.buffer).release();
    }

    @Override
    public SocketChannel reference() {
      return clientChannel;
    }

    @Override
    public ConsumerByteBuffer requestBuffer() {
      return requestBuffer;
    }

    @Override
    public ResponseData responseData() {
      final PooledByteBuffer pooled = bufferPool.access();
      final ResponseData responseData = new ResponseData(pooled);
      orderedResponseData.add(responseData);
      return responseData;
    }

    @Override
    public ResponseSenderChannel sender() {
      return responder;
    }
  }
}
