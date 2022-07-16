package io.vlingo.xoom.wire.fdx.outbound.rsocket;

import io.vlingo.xoom.actors.Logger;
import io.vlingo.xoom.wire.fdx.outbound.ManagedOutboundChannelProvider;
import io.vlingo.xoom.wire.fdx.outbound.AbstractManagedOutboundProviderTest;
import io.vlingo.xoom.wire.node.AddressType;
import io.vlingo.xoom.wire.node.Node;

public class ManagedOutboundRSocketChannelProviderTest extends AbstractManagedOutboundProviderTest {

  @Override
  protected ManagedOutboundChannelProvider getProvider(final Node node, final AddressType op, final Logger logger) {
    return new ManagedOutboundRSocketChannelProvider(node, op, logger);
  }
}