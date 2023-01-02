// Copyright © 2012-2023 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.
package io.vlingo.xoom.wire.fdx.inbound.tcp;

import io.vlingo.xoom.actors.Logger;
import io.vlingo.xoom.wire.channel.ChannelReader;
import io.vlingo.xoom.wire.fdx.inbound.InboundChannelReaderProvider;

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
