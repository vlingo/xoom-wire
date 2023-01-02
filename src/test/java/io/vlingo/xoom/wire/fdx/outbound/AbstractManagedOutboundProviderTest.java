// Copyright © 2012-2023 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.
package io.vlingo.xoom.wire.fdx.outbound;

import io.vlingo.xoom.actors.Logger;
import io.vlingo.xoom.wire.message.AbstractMessageTool;
import io.vlingo.xoom.wire.node.AddressType;
import io.vlingo.xoom.wire.node.Id;
import io.vlingo.xoom.wire.node.Node;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertNotNull;

public abstract class AbstractManagedOutboundProviderTest extends AbstractMessageTool {
  private ManagedOutboundChannelProvider provider;

  @Test
  public void testProviderProvides() {
    assertEquals(0, provider.allOtherNodeChannels().size()); // channels are lazily created

    List<Node> nodeIds = Arrays.asList(
            config.nodeMatching(Id.of(2)),
            config.nodeMatching(Id.of(3)));

    assertNotNull(provider.channelFor(nodeIds.get(0)));
    assertNotNull(provider.channelFor(nodeIds.get(1)));

    assertEquals(2, provider.channelsFor(nodeIds).size());
  }

  @Test
  public void testProviderCloseAllReopen() {
    provider.close();

    List<Node> nodeIds = Arrays.asList(
            config.nodeMatching(Id.of(3)),
            config.nodeMatching(Id.of(2)),
            config.nodeMatching(Id.of(1)));

    assertNotNull(provider.channelFor(nodeIds.get(0)));
    assertNotNull(provider.channelFor(nodeIds.get(1)));
    assertNotNull(provider.channelFor(nodeIds.get(2)));

    assertEquals(2, provider.allOtherNodeChannels().size());
  }

  @Test
  public void testProviderCloseOneChannelReopen() {
    List<Node> nodeIds = Arrays.asList(
            config.nodeMatching(Id.of(3)),
            config.nodeMatching(Id.of(2)));

    assertNotNull(provider.channelFor(nodeIds.get(0))); // channels are created on demand; create the channel
    provider.close(nodeIds.get(0).id());

    assertNotNull(provider.channelFor(nodeIds.get(0)));
    assertEquals(1, provider.allOtherNodeChannels().size());

    assertNotNull(provider.channelFor(nodeIds.get(1))); // create the channel
    assertEquals(2, provider.allOtherNodeChannels().size());
  }

  @Before
  public void setUp() {
    provider = getProvider(config.nodeMatching(Id.of(1)), AddressType.OP, testWorld.defaultLogger());
  }

  protected abstract ManagedOutboundChannelProvider getProvider(final Node node, final AddressType op, final Logger logger);

}
