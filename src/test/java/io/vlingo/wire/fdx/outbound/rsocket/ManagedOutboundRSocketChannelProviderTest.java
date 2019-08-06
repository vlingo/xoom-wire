package io.vlingo.wire.fdx.outbound.rsocket;

import io.vlingo.wire.fdx.outbound.ManagedOutboundChannelProvider;
import io.vlingo.wire.fdx.outbound.AbstractManagedOutboundProviderTest;
import io.vlingo.wire.node.AddressType;
import io.vlingo.wire.node.Configuration;
import io.vlingo.wire.node.Node;

public class ManagedOutboundRSocketChannelProviderTest extends AbstractManagedOutboundProviderTest {

  @Override
  protected ManagedOutboundChannelProvider getProvider(final Node node, final AddressType op, final Configuration config) {
    return new ManagedOutboundRSocketChannelProvider(node, op, config);
  }
}