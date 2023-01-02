// Copyright © 2012-2023 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.xoom.wire.fdx.outbound;

import io.vlingo.xoom.common.pool.ElasticResourcePool;
import io.vlingo.xoom.wire.message.AbstractMessageTool;
import io.vlingo.xoom.wire.message.ConsumerByteBuffer;
import io.vlingo.xoom.wire.message.ConsumerByteBufferPool;
import io.vlingo.xoom.wire.message.RawMessage;
import io.vlingo.xoom.wire.node.Id;
import io.vlingo.xoom.wire.node.Node;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;

public class OutboundTest extends AbstractMessageTool {
  private static final String Message1 = "Message1";
  private static final String Message2 = "Message2";
  private static final String Message3 = "Message3";
  
  private MockManagedOutboundChannelProvider channelProvider;
  private ConsumerByteBufferPool pool;
  private Outbound outbound;
  
  @Test
  public void testBroadcast() {
    final RawMessage rawMessage1 = RawMessage.from(0, 0, Message1);
    final RawMessage rawMessage2 = RawMessage.from(0, 0, Message2);
    final RawMessage rawMessage3 = RawMessage.from(0, 0, Message3);
    
    outbound.broadcast(rawMessage1);
    outbound.broadcast(rawMessage2);
    outbound.broadcast(rawMessage3);
    
    for (final ManagedOutboundChannel channel : channelProvider.allOtherNodeChannels().values()) {
      final MockManagedOutboundChannel mock = (MockManagedOutboundChannel) channel;
      
      assertEquals(Message1, mock.writes.get(0));
      assertEquals(Message2, mock.writes.get(1));
      assertEquals(Message3, mock.writes.get(2));
    }
  }
  
  @Test
  public void testBroadcastPooledByteBuffer() {
    final ConsumerByteBuffer buffer1 = pool.acquire();
    final ConsumerByteBuffer buffer2 = pool.acquire();
    final ConsumerByteBuffer buffer3 = pool.acquire();
    
    final RawMessage rawMessage1 = RawMessage.from(0, 0, Message1);
    rawMessage1.asByteBuffer(buffer1.asByteBuffer());
    final RawMessage rawMessage2 = RawMessage.from(0, 0, Message2);
    rawMessage2.asByteBuffer(buffer2.asByteBuffer());
    final RawMessage rawMessage3 = RawMessage.from(0, 0, Message3);
    rawMessage3.asByteBuffer(buffer3.asByteBuffer());

    outbound.broadcast(buffer1);
    outbound.broadcast(buffer2);
    outbound.broadcast(buffer3);
    
    for (final ManagedOutboundChannel channel : channelProvider.allOtherNodeChannels().values()) {
      final MockManagedOutboundChannel mock = (MockManagedOutboundChannel) channel;
      
      assertEquals(Message1, mock.writes.get(0));
      assertEquals(Message2, mock.writes.get(1));
      assertEquals(Message3, mock.writes.get(2));
    }
  }
  
  @Test
  public void testBroadcastToSelectNodes() {
    final RawMessage rawMessage1 = RawMessage.from(0, 0, Message1);
    final RawMessage rawMessage2 = RawMessage.from(0, 0, Message2);
    final RawMessage rawMessage3 = RawMessage.from(0, 0, Message3);
    
    final List<Node> selectNodes = asList(config.nodeMatching(Id.of(3)));
    
    outbound.broadcast(selectNodes, rawMessage1);
    outbound.broadcast(selectNodes, rawMessage2);
    outbound.broadcast(selectNodes, rawMessage3);
    
    final MockManagedOutboundChannel mock = (MockManagedOutboundChannel) channelProvider.channelFor(selectNodes.get(0));
    
    assertEquals(Message1, mock.writes.get(0));
    assertEquals(Message2, mock.writes.get(1));
    assertEquals(Message3, mock.writes.get(2));
  }
  
  @Test
  public void testSendTo() {
    final RawMessage rawMessage1 = RawMessage.from(0, 0, Message1);
    final RawMessage rawMessage2 = RawMessage.from(0, 0, Message2);
    final RawMessage rawMessage3 = RawMessage.from(0, 0, Message3);
    
    final Node node3 = config.nodeMatching(Id.of(3));

    outbound.sendTo(rawMessage1, node3);
    outbound.sendTo(rawMessage2, node3);
    outbound.sendTo(rawMessage3, node3);
    
    final MockManagedOutboundChannel mock = (MockManagedOutboundChannel) channelProvider.channelFor(node3);
    
    assertEquals(Message1, mock.writes.get(0));
    assertEquals(Message2, mock.writes.get(1));
    assertEquals(Message3, mock.writes.get(2));
  }
  
  @Test
  public void testSendToPooledByteBuffer() {
    final ConsumerByteBuffer buffer1 = pool.acquire();
    final ConsumerByteBuffer buffer2 = pool.acquire();
    final ConsumerByteBuffer buffer3 = pool.acquire();
    
    final RawMessage rawMessage1 = RawMessage.from(0, 0, Message1);
    rawMessage1.asByteBuffer(buffer1.asByteBuffer());
    final RawMessage rawMessage2 = RawMessage.from(0, 0, Message2);
    rawMessage2.asByteBuffer(buffer2.asByteBuffer());
    final RawMessage rawMessage3 = RawMessage.from(0, 0, Message3);
    rawMessage3.asByteBuffer(buffer3.asByteBuffer());

    final Node node3 = config.nodeMatching(Id.of(3));
    
    outbound.sendTo(buffer1, node3);
    outbound.sendTo(buffer2, node3);
    outbound.sendTo(buffer3, node3);
    
    final MockManagedOutboundChannel mock = (MockManagedOutboundChannel) channelProvider.channelFor(node3);
    
    assertEquals(Message1, mock.writes.get(0));
    assertEquals(Message2, mock.writes.get(1));
    assertEquals(Message3, mock.writes.get(2));
  }
  
  @Before
  public void setUp() {
    pool = new ConsumerByteBufferPool(
        ElasticResourcePool.Config.of(10), 1024);
    channelProvider = new MockManagedOutboundChannelProvider(Id.of(1), config);
    outbound = new Outbound(channelProvider, new ConsumerByteBufferPool(
        ElasticResourcePool.Config.of(10), 10_000));
  }
}
