// Copyright © 2012-2020 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.fdx.inbound;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.vlingo.actors.Definition;
import io.vlingo.actors.testkit.TestActor;
import io.vlingo.actors.testkit.TestWorld;
import io.vlingo.wire.channel.MockChannelReader;
import io.vlingo.wire.fdx.inbound.InboundStream.InboundStreamInstantiator;
import io.vlingo.wire.message.AbstractMessageTool;
import io.vlingo.wire.node.AddressType;

public class InboundStreamTest extends AbstractMessageTool {
  private TestActor<InboundStream> inboundStream;
  private MockInboundStreamInterest interest;
  private MockChannelReader reader;
  private TestWorld world;

  @Test
  public void testInbound() throws Exception {
    interest.testResults.untilStops = TestUntil.happenings(1);
    while (reader.probeChannelCount.get() == 0)
      ;
    inboundStream.actor().stop();
    int count;
    for (count = 0; count < (int) interest.testResults.access.readFrom("messageCount"); count++) {
      String message = interest.testResults.access.readFrom("messages", count);
      assertEquals(MockChannelReader.MessagePrefix + (count + 1), message);
    }

    assertTrue(interest.testResults.messageCount.get() > 0);
    assertEquals(count, reader.probeChannelCount.get());
  }

  @Before
  public void setUp() throws Exception {
    world = TestWorld.start("test-inbound-stream");

    interest = new MockInboundStreamInterest();

    reader = new MockChannelReader();

    final Definition definition =
            Definition.has(
                    InboundStreamActor.class,
                    new InboundStreamInstantiator(interest, AddressType.OP, reader, 10),
                    "test-inbound");

    inboundStream = world.actorFor(InboundStream.class, definition);
  }

  @After
  public void tearDown() {
    world.terminate();
  }
}
