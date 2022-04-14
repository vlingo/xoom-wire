// Copyright © 2012-2022 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.xoom.wire.channel;

import io.vlingo.xoom.actors.Logger;
import io.vlingo.xoom.wire.message.RawMessage;
import io.vlingo.xoom.wire.node.Address;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class SocketChannelWriter {
  private SocketChannel channel;
  private final Address address;
  private final Logger logger;

  public SocketChannelWriter(final Address address, final Logger logger) {
    this.address = address;
    this.logger = logger;
    this.channel = null;
  }

  public void close() {
    if (channel != null) {
      try {
        channel.close();
      } catch (Exception e) {
        logger.error("Channel close failed because: " + e.getMessage(), e);
      }
    }
    channel = null;
  }

  public int write(final RawMessage message, final ByteBuffer buffer) {
    buffer.clear();
    message.copyBytesTo(buffer);
    buffer.flip();
    return write(buffer);
  }

  public int write(final ByteBuffer buffer) {
    final SocketChannel preparedChannel = prepareChannel();
    int totalBytesWritten = 0;

    if (preparedChannel != null) {
      try {
        while (buffer.hasRemaining()) {
          totalBytesWritten += preparedChannel.write(buffer);
        }
      } catch (Exception e) {
        logger.error("Write to channel failed because: " + e.getMessage(), e);
        close();
      }
    }
    return totalBytesWritten;
  }

  @Override
  public String toString() {
    return "SocketChannelWriter[address=" + address + ", channel=" + channel + "]";
  }

  private SocketChannel prepareChannel() {
    try {
      if (channel != null) {
        if (channel.isConnected()) {
          return channel;
        } else {
          close();
        }
      } else {
        channel = SocketChannel.open();
        channel.connect(new InetSocketAddress(address.hostName(), address.port()));
        return channel;
      }
    } catch (Exception e) {
      logger.error("" + this + ": Failed to prepare channel because: " + e.getMessage(), e);
      close();
    }
    return null;
  }
}
