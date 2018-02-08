// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.fdx.inbound;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.vlingo.actors.Definition;
import io.vlingo.actors.testkit.TestActor;
import io.vlingo.actors.testkit.TestWorld;
import io.vlingo.wire.channel.MockChannelReader;
import io.vlingo.wire.message.AbstractMessageTool;
import io.vlingo.wire.node.AddressType;

public class InboundStreamTest extends AbstractMessageTool {
  private TestActor<InboundStream> inboundStream;
  private MockInboundStreamInterest interest;
  private MockChannelReader reader;
  private TestWorld world;
  
  @Test
  public void testInbound() throws Exception {
    inboundStream.actor().start();
    pause();
    assertTrue(interest.messageCount > 0);
    final List<String> copy = new ArrayList<>(interest.messages);
    int count = 0;
    for (final String message : copy) {
      ++count;
      assertEquals(MockChannelReader.MessagePrefix + count, message);
    }
    
    assertEquals(count, reader.probeChannelCount);
  }

  @Before
  public void setUp() throws Exception {
    world = TestWorld.start("test-inbound-stream");
    
    interest = new MockInboundStreamInterest();
    
    reader = new MockChannelReader();
    
    final Definition definition =
            Definition.has(
                    InboundStreamActor.class,
                    Definition.parameters(interest, AddressType.OP, reader, 10),
                    "test-inbound");
    
    inboundStream = world.actorFor(definition, InboundStream.class);
  }
  
  @After
  public void tearDown() {
    world.terminate();
  }
  
  private void pause() {
    try { Thread.sleep(1000); } catch (Exception e) { }
  }
}
