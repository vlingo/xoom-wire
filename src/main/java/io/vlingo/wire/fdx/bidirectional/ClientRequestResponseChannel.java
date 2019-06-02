// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.fdx.bidirectional;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import io.vlingo.actors.Logger;
import io.vlingo.wire.channel.RequestSenderChannel;
import io.vlingo.wire.channel.ResponseChannelConsumer;
import io.vlingo.wire.channel.ResponseListenerChannel;
import io.vlingo.wire.message.ByteBufferPool;
import io.vlingo.wire.message.ConsumerByteBuffer;
import io.vlingo.wire.node.Address;

public abstract class ClientRequestResponseChannel implements RequestSenderChannel, ResponseListenerChannel {
  protected final Address address;
  protected final ResponseChannelConsumer consumer;
  protected final Logger logger;
  protected ByteBufferPool readBufferPool;

  private SocketChannel channel;
  private boolean closed;
  private final int maxBufferPoolSize;
  private int maxMessageSize;

  public ClientRequestResponseChannel(
          final Address address,
          final ResponseChannelConsumer consumer,
          final int maxBufferPoolSize,
          final int maxMessageSize,
          final Logger logger)
  throws Exception {
    this.address = address;
    this.consumer = consumer;
    this.maxBufferPoolSize = maxBufferPoolSize;
    this.maxMessageSize = maxMessageSize;
    this.logger = logger;
    this.closed = false;
  }

  //=========================================
  // RequestSenderChannel
  //=========================================

  @Override
  public void close() {
    if (closed) return;

    closed = true;

    closeChannel();
  }

  @Override
  public void requestWith(final ByteBuffer buffer) {
    final SocketChannel preparedChannel = preparedChannel();

    if (preparedChannel != null) {
      try {
        while (buffer.hasRemaining()) {
          preparedChannel.write(buffer);
        }
      } catch (Exception e) {
        logger.log("Write to socket failed because: " + e.getMessage(), e);
        closeChannel();
      }
    }
  }


  //=========================================
  // ResponseListenerChannel
  //=========================================

  @Override
  public void probeChannel() {
    if (closed) return;

    try {
      final SocketChannel channel = preparedChannel();
      if (channel != null) {
        readConsume(channel);
      }
    } catch (IOException e) {
      logger.log("Failed to read channel selector for " + address + " because: " + e.getMessage(), e);
    }
  }

  //=========================================
  // internal implementation
  //=========================================

  protected Address address() {
    return address;
  }

  protected SocketChannel channel() {
    return channel;
  }

  protected void closeChannel() {
    if (channel != null) {
      try {
        channel.close();
      } catch (Exception e) {
        logger.log("Failed to close channel to " + address + " because: " + e.getMessage(), e);
      }
    }
    channel = null;
  }

  protected Logger logger() {
    return logger;
  }

  protected int maxMessageSize() {
    return maxMessageSize;
  }

  protected void maxMessageSize(final int maxMessageSize) {
    this.maxMessageSize = maxMessageSize;
  }

  protected SocketChannel preparedChannel() {
    final SocketChannel prepared = preparedChannelDelegate();
    this.channel = prepared;
    return prepared;
  }

  protected abstract SocketChannel preparedChannelDelegate();

  private void readConsume(final SocketChannel channel) throws IOException {
    final ConsumerByteBuffer pooledBuffer = pooledByteBuffer();
    final ByteBuffer readBuffer = pooledBuffer.asByteBuffer();
    int totalBytesRead = 0;
    int bytesRead = 0;
    try {
      do {
        bytesRead = channel.read(readBuffer);
        totalBytesRead += bytesRead;
      } while (bytesRead > 0);

      if (totalBytesRead > 0) {
        consumer.consume(pooledBuffer.flip());
      } else {
        pooledBuffer.release();
      }
    } catch (Exception e) {
      pooledBuffer.release();
      throw e;
    }
  }

  private ConsumerByteBuffer pooledByteBuffer() {
    if (readBufferPool == null) {
      readBufferPool = new ByteBufferPool(maxBufferPoolSize, maxMessageSize);
    }
    return readBufferPool.accessFor("client-response", 25);
  }
}
