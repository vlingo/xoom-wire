// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.fdx.outbound.tcp;

import io.vlingo.wire.fdx.outbound.AbstractManagedOutboundProviderTest;
import io.vlingo.wire.fdx.outbound.ManagedOutboundChannelProvider;
import io.vlingo.wire.fdx.outbound.tcp.ManagedOutboundSocketChannelProvider;
import io.vlingo.wire.node.AddressType;
import io.vlingo.wire.node.Configuration;
import io.vlingo.wire.node.Node;

public class ManagedOutboundSocketChannelProviderTest extends AbstractManagedOutboundProviderTest {
  @Override
  protected ManagedOutboundChannelProvider getProvider(final Node node, final AddressType op, final Configuration config) {
    return new ManagedOutboundSocketChannelProvider(node, op, config);
  }
}
