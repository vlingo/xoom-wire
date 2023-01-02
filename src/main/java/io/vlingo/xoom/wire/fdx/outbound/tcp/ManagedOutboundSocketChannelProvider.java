// Copyright © 2012-2023 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.xoom.wire.fdx.outbound.tcp;

import io.vlingo.xoom.actors.Logger;
import io.vlingo.xoom.wire.fdx.outbound.AbstractManagedOutboundChannelProvider;
import io.vlingo.xoom.wire.fdx.outbound.ManagedOutboundChannel;
import io.vlingo.xoom.wire.node.Address;
import io.vlingo.xoom.wire.node.AddressType;
import io.vlingo.xoom.wire.node.Node;

public class ManagedOutboundSocketChannelProvider extends AbstractManagedOutboundChannelProvider {
  public ManagedOutboundSocketChannelProvider(final Node node, final AddressType type, final Logger logger) {
    super(node, type, logger);
  }

  @Override
  protected ManagedOutboundChannel unopenedChannelFor(final Node node, final Address nodeAddress, final Logger logger) {
    return new ManagedOutboundSocketChannel(node, nodeAddress, logger);
  }
}
