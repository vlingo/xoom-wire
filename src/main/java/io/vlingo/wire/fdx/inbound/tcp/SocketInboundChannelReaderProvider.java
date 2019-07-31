// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.
package io.vlingo.wire.fdx.inbound.tcp;

import io.vlingo.actors.Logger;
import io.vlingo.wire.channel.ChannelReader;
import io.vlingo.wire.fdx.inbound.InboundChannelReaderProvider;

public class SocketInboundChannelReaderProvider implements InboundChannelReaderProvider {
  private final int maxMessageSize;
  private final Logger logger;

  public SocketInboundChannelReaderProvider(final int maxMessageSize, final Logger logger) {
    this.maxMessageSize = maxMessageSize;
    this.logger = logger;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ChannelReader channelFor(final int port, final String name) throws Exception {
    return new SocketChannelInboundReader(port, name, maxMessageSize, logger);
  }
}
