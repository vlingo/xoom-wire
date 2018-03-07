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
import java.nio.channels.SocketChannel;

import io.vlingo.actors.Logger;
import io.vlingo.wire.channel.RequestSenderChannel;
import io.vlingo.wire.channel.ResponseChannelConsumer;
import io.vlingo.wire.channel.ResponseListenerChannel;
import io.vlingo.wire.message.ByteBufferAllocator;
import io.vlingo.wire.node.Address;

public class ClientRequestResponseChannel implements RequestSenderChannel, ResponseListenerChannel {
  private final Address address;
  private SocketChannel channel;
  private boolean closed;
  private final ResponseChannelConsumer consumer;
  private final Logger logger;
  private final ByteBuffer readBuffer;

  public ClientRequestResponseChannel(
          final Address address,
          final ResponseChannelConsumer consumer,
          final int maxMessageSize,
          final Logger logger)
  throws Exception {
    this.address = address;
    this.consumer = consumer;
    this.logger = logger;
    this.closed = false;
    this.channel = null;
    this.readBuffer = ByteBufferAllocator.allocate(maxMessageSize);
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
        int totalBytesRead = 0;
        int bytesRead = 0;
        do {
          bytesRead = channel.read(readBuffer);
          totalBytesRead += bytesRead;
        } while (bytesRead > 0);

        if (totalBytesRead > 0 && readBuffer.limit() > 0) {
          readBuffer.flip();
          consumer.consume(readBuffer);
          readBuffer.clear();
        }
      }
    } catch (IOException e) {
      logger.log("Failed to read channel selector for " + address + " because: " + e.getMessage(), e);
    }
  }

  //=========================================
  // internal implementation
  //=========================================

  private void closeChannel() {
    if (channel != null) {
      try {
        channel.close();
      } catch (Exception e) {
        logger.log("Failed to close channel to " + address + " because: " + e.getMessage(), e);
      }
    }
    channel = null;
  }

  private SocketChannel preparedChannel() {
    try {
      if (channel != null) {
        if (channel.isConnected()) {
          return channel;
        } else {
          closeChannel();
        }
      } else {
        channel = SocketChannel.open();
        channel.connect(new InetSocketAddress(address.hostName(), address.port()));
        channel.configureBlocking(false);
        return channel;
      }
    } catch (Exception e) {
      closeChannel();
    }
    return null;
  }
}
