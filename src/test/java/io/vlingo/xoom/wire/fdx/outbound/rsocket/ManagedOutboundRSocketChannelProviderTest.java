package io.vlingo.xoom.wire.fdx.outbound.rsocket;

import io.vlingo.xoom.wire.fdx.outbound.ManagedOutboundChannelProvider;
import io.vlingo.xoom.wire.fdx.outbound.AbstractManagedOutboundProviderTest;
import io.vlingo.xoom.wire.node.AddressType;
import io.vlingo.xoom.wire.node.Configuration;
import io.vlingo.xoom.wire.node.Node;

public class ManagedOutboundRSocketChannelProviderTest extends AbstractManagedOutboundProviderTest {

  @Override
  protected ManagedOutboundChannelProvider getProvider(final Node node, final AddressType op, final Configuration config) {
    return new ManagedOutboundRSocketChannelProvider(node, op, config);
  }
}