// Copyright © 2012-2022 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.xoom.wire.fdx.bidirectional;

import io.vlingo.xoom.actors.Logger;
import io.vlingo.xoom.common.pool.ElasticResourcePool;
import io.vlingo.xoom.wire.channel.ResponseChannelConsumer;
import io.vlingo.xoom.wire.message.ConsumerByteBuffer;
import io.vlingo.xoom.wire.message.ConsumerByteBufferPool;
import io.vlingo.xoom.wire.node.Address;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * Deprecated. Use {@link io.vlingo.xoom.wire.fdx.bidirectional.netty.client.NettyClientRequestResponseChannel}.
 */
@Deprecated
public class BasicClientRequestResponseChannel implements ClientRequestResponseChannel {
  private final Address address;
  private final ResponseChannelConsumer consumer;
  private final Logger logger;
  private final ConsumerByteBufferPool readBufferPool;

  private SocketChannel channel;
  private int previousPrepareFailures;

  public BasicClientRequestResponseChannel(
          final Address address,
          final ResponseChannelConsumer consumer,
          final int maxBufferPoolSize,
          final int maxMessageSize,
          final Logger logger)
  throws Exception {
    this.address = address;
    this.consumer = consumer;
    this.logger = logger;
    this.readBufferPool = new ConsumerByteBufferPool(
        ElasticResourcePool.Config.of(maxBufferPoolSize), maxMessageSize);
    this.previousPrepareFailures = 0;
  }

  //=========================================
  // RequestSenderChannel
  //=========================================

  @Override
  public void close() {
    if (channel != null) {
      try {
        channel.close();
      } catch (Exception e) {
        logger.error("Failed to close channel to " + address + " because: " + e.getMessage(), e);
      }
    }
    channel = null;
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
        logger.error("Write to socket failed because: " + e.getMessage(), e);
        close();
      }
    }
  }


  //=========================================
  // ResponseListenerChannel
  //=========================================

  @Override
  public void probeChannel() {
    try {
      final SocketChannel channel = preparedChannel();
      if (channel != null) {
        readConsume(channel);
      }
    } catch (IOException e) {
      logger.error("Failed to read channel selector for " + address + " because: " + e.getMessage(), e);
    }
  }

  //=========================================
  // internal implementation
  //=========================================

  private SocketChannel preparedChannel() {
    try {
      if (channel != null) {
        if (channel.isConnected()) {
          previousPrepareFailures = 0;
          return channel;
        } else {
          close();
        }
      } else {
        channel = SocketChannel.open();
        channel.connect(new InetSocketAddress(address.hostName(), address.port()));
        channel.configureBlocking(false);
        previousPrepareFailures = 0;
        return channel;
      }
    } catch (Exception e) {
      close();
      final String message = getClass().getSimpleName() + ": Cannot prepare/open channel because: " + e.getMessage();
      if (previousPrepareFailures == 0) {
        logger.error(message, e);
      } else if (previousPrepareFailures % 20 == 0) {
        logger.info("AGAIN: " + message);
      }
    }
    ++previousPrepareFailures;
    return null;
  }

  private void readConsume(final SocketChannel channel) throws IOException {
    ConsumerByteBuffer pooledBuffer = null;
    ByteBuffer readBuffer = null;
    int totalBytesRead = 0;
    int bytesRead = 0;
    try {
      pooledBuffer = readBufferPool.acquire("BasicClientRequestResponseChannel#readConsume");
      readBuffer = pooledBuffer.asByteBuffer();
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
      if (pooledBuffer != null){
        pooledBuffer.release();
      }
      throw e;
    }
  }
}
