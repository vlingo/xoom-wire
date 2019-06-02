// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.fdx.bidirectional;

import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;

import io.vlingo.actors.Logger;
import io.vlingo.wire.channel.ResponseChannelConsumer;
import io.vlingo.wire.node.Address;

public class BasicClientRequestResponseChannel extends ClientRequestResponseChannel {
  private int previousPrepareFailures;

  public BasicClientRequestResponseChannel(
          final Address address,
          final ResponseChannelConsumer consumer,
          final int maxBufferPoolSize,
          final int maxMessageSize,
          final Logger logger)
  throws Exception {
    super(address, consumer, maxBufferPoolSize, maxMessageSize, logger);

    this.previousPrepareFailures = 0;
  }

  /**
   * @see io.vlingo.wire.fdx.bidirectional.ClientRequestResponseChannel#preparedChannelDelegate()
   */
  @Override
  protected SocketChannel preparedChannelDelegate() {
    SocketChannel channel = channel();

    try {
      if (channel != null) {
        if (channel.isConnected()) {
          previousPrepareFailures = 0;
          return channel;
        } else {
          closeChannel();
        }
      } else {
        channel = SocketChannel.open();
        channel.connect(new InetSocketAddress(address().hostName(), address().port()));
        channel.configureBlocking(false);
        previousPrepareFailures = 0;
        return channel;
      }
    } catch (Exception e) {
      closeChannel();
      final String message = getClass().getSimpleName() + ": Cannot prepare/open channel because: " + e.getMessage();
      if (previousPrepareFailures == 0) {
        logger().log(message, e);
      } else if (previousPrepareFailures % 20 == 0) {
        logger().log("AGAIN: " + message);
      }
    }
    ++previousPrepareFailures;
    return null;
  }
}
