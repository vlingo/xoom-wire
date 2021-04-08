// Copyright © 2012-2021 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.xoom.wire.fdx.outbound.tcp;

import io.vlingo.xoom.wire.fdx.outbound.AbstractManagedOutboundProviderTest;
import io.vlingo.xoom.wire.fdx.outbound.ManagedOutboundChannelProvider;
import io.vlingo.xoom.wire.node.AddressType;
import io.vlingo.xoom.wire.node.Configuration;
import io.vlingo.xoom.wire.node.Node;

public class ManagedOutboundSocketChannelProviderTest extends AbstractManagedOutboundProviderTest {
  @Override
  protected ManagedOutboundChannelProvider getProvider(final Node node, final AddressType op, final Configuration config) {
    return new ManagedOutboundSocketChannelProvider(node, op, config);
  }
}
