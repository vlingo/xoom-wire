// Copyright © 2012-2023 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.xoom.wire.fdx.inbound;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import io.vlingo.xoom.wire.fdx.inbound.MockInboundStreamInterest.TestResults;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.vlingo.xoom.actors.Definition;
import io.vlingo.xoom.actors.testkit.TestActor;
import io.vlingo.xoom.actors.testkit.TestWorld;
import io.vlingo.xoom.wire.channel.MockChannelReader;
import io.vlingo.xoom.wire.fdx.inbound.InboundStream.InboundStreamInstantiator;
import io.vlingo.xoom.wire.message.AbstractMessageTool;
import io.vlingo.xoom.wire.node.AddressType;

public class InboundStreamTest extends AbstractMessageTool {
  private TestActor<InboundStream> inboundStream;
  private MockInboundStreamInterest interest;
  private MockChannelReader reader;
  private TestWorld world;

  @Test
  public void testInbound() throws Exception {
    interest.testResults = new TestResults(1);
    reader.results = new MockChannelReader.Results(1);
    while ((int) reader.results.access.readFrom("probeChannelCount") == 0)
      ;
    inboundStream.actor().stop();
    int count;
    for (count = 0; count < (int) interest.testResults.access.readFrom("messageCount"); count++) {
      String message = interest.testResults.access.readFrom("messages", count);
      assertEquals(MockChannelReader.MessagePrefix + (count + 1), message);
    }

    assertTrue((int) interest.testResults.access.readFrom("messageCount") > 0);
    assertEquals(count, (int) reader.results.access.readFrom("probeChannelCount"));
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
