// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.fdx.bidirectional;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;

import io.vlingo.actors.Logger;
import io.vlingo.wire.channel.ResponseChannelConsumer;
import io.vlingo.wire.message.ByteBufferPool;
import io.vlingo.wire.message.ConsumerByteBuffer;
import io.vlingo.wire.node.Address;

public class SecureClientRequestResponseChannel implements ClientRequestResponseChannel {
  private final Address address;
  private final ResponseChannelConsumer consumer;
  private final Logger logger;
  private final ByteBufferPool readBufferPool;

  private AsynchronousSocketChannel channel;
  private int previousPrepareFailures;

  public SecureClientRequestResponseChannel(
          final Address address,
          final ResponseChannelConsumer consumer,
          final int maxBufferPoolSize,
          final int maxMessageSize,
          final Logger logger)
  throws Exception {
    this.address = address;
    this.consumer = consumer;
    this.logger = logger;
    this.readBufferPool = new ByteBufferPool(maxBufferPoolSize, maxMessageSize);
  }

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
    final AsynchronousSocketChannel preparedChannel = prepareChannel();

    if (preparedChannel != null) {
      preparedChannel.write(buffer, this, new CompletionHandler<Integer, SecureClientRequestResponseChannel>() {
        @Override
        public void completed(final Integer result, final SecureClientRequestResponseChannel secure) {
          if (buffer.hasRemaining()) {
            SecureClientRequestResponseChannel.this.channel.write(buffer, secure, this);
          }
        }

        @Override
        public void failed(final Throwable exception, final SecureClientRequestResponseChannel secure) {
          SecureClientRequestResponseChannel.this.close();
          throw new IllegalStateException(getClass().getSimpleName() + ": Could not write to channel: " + channel, exception);
        }
      });
    }
  }

  @Override
  public void probeChannel() {
    final AsynchronousSocketChannel preparedChannel = prepareChannel();

    if (preparedChannel != null) {
      final ConsumerByteBuffer buffer = readBufferPool.accessFor("client-response", 25);
      preparedChannel.read(buffer.asByteBuffer(), this, new CompletionHandler<Integer, SecureClientRequestResponseChannel>() {
        @Override
        public void completed(final Integer result, final SecureClientRequestResponseChannel secure) {
          if (result >= 0) {
            consumer.consume(buffer);
          } else {
            buffer.release();
          }
        }

        @Override
        public void failed(final Throwable exception, final SecureClientRequestResponseChannel secure) {
          exception.printStackTrace();
          buffer.release();
          SecureClientRequestResponseChannel.this.close();
          throw new IllegalStateException(getClass().getSimpleName() + ": Could not read from channel: " + channel, exception);
        }
      });
    }
  }

  private AsynchronousSocketChannel prepareChannel() {
    try {
      if (channel != null) {
        if (channel.isOpen()) {
          previousPrepareFailures = 0;
          return channel;
        } else {
          close();
        }
      } else {
        this.channel = AsynchronousSocketChannel.open();
        final InetSocketAddress hostAddress = new InetSocketAddress(address.hostName(), address.port());
        this.channel.connect(hostAddress)
                    .get(); // DOH!
        return this.channel;
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
}
