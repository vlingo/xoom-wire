// Copyright © 2012-2020 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.fdx.outbound.rsocket;

import io.rsocket.transport.ClientTransport;
import io.rsocket.transport.netty.client.TcpClientTransport;
import io.vlingo.wire.fdx.outbound.AbstractManagedOutboundChannelProvider;
import io.vlingo.wire.fdx.outbound.ManagedOutboundChannel;
import io.vlingo.wire.node.Address;
import io.vlingo.wire.node.AddressType;
import io.vlingo.wire.node.Configuration;
import io.vlingo.wire.node.Node;

public class ManagedOutboundRSocketChannelProvider extends AbstractManagedOutboundChannelProvider {

  private static ClientTransport transportFor(final Address address) {
    return TcpClientTransport.create(address.hostName(), address.port());
  }

  public ManagedOutboundRSocketChannelProvider(final Node node, final AddressType type, final Configuration configuration) {
    super(node, type, configuration);
  }

  @Override
  protected ManagedOutboundChannel unopenedChannelFor(final Node node, final Configuration configuration, final AddressType type) {
    final Address address = addressOf(node, type);
    return new RSocketOutboundChannel(address, transportFor(address), configuration.logger());
  }

}
