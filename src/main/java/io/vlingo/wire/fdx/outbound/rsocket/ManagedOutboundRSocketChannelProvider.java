// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.fdx.outbound.rsocket;

import io.vlingo.wire.fdx.outbound.AbstractManagedOutboundChannelProvider;
import io.vlingo.wire.fdx.outbound.ManagedOutboundChannel;
import io.vlingo.wire.node.Address;
import io.vlingo.wire.node.AddressType;
import io.vlingo.wire.node.Configuration;
import io.vlingo.wire.node.Node;

public class ManagedOutboundRSocketChannelProvider extends AbstractManagedOutboundChannelProvider  {
  public ManagedOutboundRSocketChannelProvider(final Node node, final AddressType type, final Configuration configuration) {
    super(node, type, configuration);
  }

  @Override
  protected ManagedOutboundChannel unopenedChannelFor(final Node node, final Configuration configuration, final AddressType type) {
    final Address address = (type == AddressType.OP ? node.operationalAddress() : node.applicationAddress());

    return new RSocketOutboundChannel(address, configuration.logger());
  }
}
