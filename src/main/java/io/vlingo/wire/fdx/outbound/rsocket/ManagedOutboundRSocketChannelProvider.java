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

import java.util.function.Function;

public class ManagedOutboundRSocketChannelProvider extends AbstractManagedOutboundChannelProvider {

  private final Function<Address, ClientTransport> clientTransportProvider;

  private ClientTransport transportFor(final Address address) {
    return this.clientTransportProvider.apply(address);
  }

  /**
   * Build a instance of provider that will create {@link RSocketOutboundChannel} using the default RSocket client transport, {@link TcpClientTransport}.
   *
   * @param node the outbound node to connect to
   * @param type the address type
   * @param configuration current node configuration
   */
  public ManagedOutboundRSocketChannelProvider(final Node node, final AddressType type, final Configuration configuration) {
    super(node, type, configuration);
    this.clientTransportProvider = address -> TcpClientTransport.create(address.hostName(), address.port());
  }

  /**
   * Build a instance of provider that will create {@link RSocketOutboundChannel} using a different RSocket client transport.
   *
   * @param node the outbound node to connect to
   * @param type the address type
   * @param configuration current node configuration
   * @param clientTransportProvider function that given a remote node address, returns a instance of {@link ClientTransport}
   */
  public ManagedOutboundRSocketChannelProvider(final Node node, final AddressType type, final Configuration configuration,
                                               final Function<Address, ClientTransport> clientTransportProvider) {
    super(node, type, configuration);
    this.clientTransportProvider = clientTransportProvider;
  }

  @Override
  protected ManagedOutboundChannel unopenedChannelFor(final Node node, final Configuration configuration, final AddressType type) {
    final Address address = addressOf(node, type);
    return new RSocketOutboundChannel(address, transportFor(address), configuration.logger());
  }

}
