// Copyright © 2012-2022 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.
package io.vlingo.xoom.wire.fdx.outbound;

import io.vlingo.xoom.wire.message.AbstractMessageTool;
import io.vlingo.xoom.wire.node.AddressType;
import io.vlingo.xoom.wire.node.Configuration;
import io.vlingo.xoom.wire.node.Id;
import io.vlingo.xoom.wire.node.Node;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertNotNull;

public abstract class AbstractManagedOutboundProviderTest extends AbstractMessageTool {
  private Collection<Node> allOtherNodes;
  private ManagedOutboundChannelProvider provider;

  @Test
  public void testProviderProvides() throws Exception {
    assertEquals(2, provider.allOtherNodeChannels().size());

    assertNotNull(provider.channelFor(Id.of(2)));

    assertNotNull(provider.channelFor(Id.of(3)));

    assertEquals(2, provider.channelsFor(allOtherNodes).size());
  }

  @Test
  public void testProviderCloseAllReopen() throws Exception {
    provider.close();

    assertNotNull(provider.channelFor(Id.of(3)));
    assertNotNull(provider.channelFor(Id.of(2)));
    assertNotNull(provider.channelFor(Id.of(1)));

    assertEquals(2, provider.allOtherNodeChannels().size());
  }

  @Test
  public void testProviderCloseOneChannelReopen() throws Exception {
    provider.close(Id.of(3));

    assertNotNull(provider.channelFor(Id.of(3)));

    assertEquals(2, provider.allOtherNodeChannels().size());
  }

  @Before
  public void setUp() throws Exception {
    allOtherNodes = config.allOtherNodes(Id.of(1));

    provider = getProvider(config.nodeMatching(Id.of(1)), AddressType.OP, config);
  }

  protected abstract ManagedOutboundChannelProvider getProvider(final Node node, final AddressType op, final Configuration config);

}
