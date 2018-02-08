// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.fdx.outbound;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import io.vlingo.wire.message.AbstractMessageTool;
import io.vlingo.wire.message.ByteBufferPool;
import io.vlingo.wire.message.ByteBufferPool.PooledByteBuffer;
import io.vlingo.wire.message.RawMessage;
import io.vlingo.wire.node.Id;
import io.vlingo.wire.node.Node;

public class OutboundTest extends AbstractMessageTool {
  private static final String Message1 = "Message1";
  private static final String Message2 = "Message2";
  private static final String Message3 = "Message3";
  
  private MockManagedOutboundChannelProvider channelProvider;
  private ByteBufferPool pool;
  private Outbound outbound;
  
  @Test
  public void testBroadcast() throws Exception {
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
  public void testBroadcastPooledByteBuffer() throws Exception {
    final PooledByteBuffer buffer1 = pool.access();
    final PooledByteBuffer buffer2 = pool.access();
    final PooledByteBuffer buffer3 = pool.access();
    
    final RawMessage rawMessage1 = RawMessage.from(0, 0, Message1);
    rawMessage1.asByteBuffer(buffer1.buffer());
    final RawMessage rawMessage2 = RawMessage.from(0, 0, Message2);
    rawMessage2.asByteBuffer(buffer2.buffer());
    final RawMessage rawMessage3 = RawMessage.from(0, 0, Message3);
    rawMessage3.asByteBuffer(buffer3.buffer());

    outbound.broadcast(buffer1);
    buffer1.release();
    outbound.broadcast(buffer2);
    buffer2.release();
    outbound.broadcast(buffer3);
    buffer3.release();
    
    for (final ManagedOutboundChannel channel : channelProvider.allOtherNodeChannels().values()) {
      final MockManagedOutboundChannel mock = (MockManagedOutboundChannel) channel;
      
      assertEquals(Message1, mock.writes.get(0));
      assertEquals(Message2, mock.writes.get(1));
      assertEquals(Message3, mock.writes.get(2));
    }
  }
  
  @Test
  public void testBroadcastToSelectNodes() throws Exception {
    final RawMessage rawMessage1 = RawMessage.from(0, 0, Message1);
    final RawMessage rawMessage2 = RawMessage.from(0, 0, Message2);
    final RawMessage rawMessage3 = RawMessage.from(0, 0, Message3);
    
    final List<Node> selectNodes = asList(config.nodeMatching(Id.of(3)));
    
    outbound.broadcast(selectNodes, rawMessage1);
    outbound.broadcast(selectNodes, rawMessage2);
    outbound.broadcast(selectNodes, rawMessage3);
    
    final MockManagedOutboundChannel mock = (MockManagedOutboundChannel) channelProvider.channelFor(Id.of(3));
    
    assertEquals(Message1, mock.writes.get(0));
    assertEquals(Message2, mock.writes.get(1));
    assertEquals(Message3, mock.writes.get(2));
  }
  
  @Test
  public void testSendTo() throws Exception {
    final RawMessage rawMessage1 = RawMessage.from(0, 0, Message1);
    final RawMessage rawMessage2 = RawMessage.from(0, 0, Message2);
    final RawMessage rawMessage3 = RawMessage.from(0, 0, Message3);
    
    final Id id3 = Id.of(3);
    
    outbound.sendTo(rawMessage1, id3);
    outbound.sendTo(rawMessage2, id3);
    outbound.sendTo(rawMessage3, id3);
    
    final MockManagedOutboundChannel mock = (MockManagedOutboundChannel) channelProvider.channelFor(Id.of(3));
    
    assertEquals(Message1, mock.writes.get(0));
    assertEquals(Message2, mock.writes.get(1));
    assertEquals(Message3, mock.writes.get(2));
  }
  
  @Test
  public void testSendToPooledByteBuffer() throws Exception {
    final PooledByteBuffer buffer1 = pool.access();
    final PooledByteBuffer buffer2 = pool.access();
    final PooledByteBuffer buffer3 = pool.access();
    
    final RawMessage rawMessage1 = RawMessage.from(0, 0, Message1);
    rawMessage1.asByteBuffer(buffer1.buffer());
    final RawMessage rawMessage2 = RawMessage.from(0, 0, Message2);
    rawMessage2.asByteBuffer(buffer2.buffer());
    final RawMessage rawMessage3 = RawMessage.from(0, 0, Message3);
    rawMessage3.asByteBuffer(buffer3.buffer());
    
    final Id id3 = Id.of(3);
    
    outbound.sendTo(buffer1, id3);
    buffer1.release();
    outbound.sendTo(buffer2, id3);
    buffer2.release();
    outbound.sendTo(buffer3, id3);
    buffer3.release();
    
    final MockManagedOutboundChannel mock = (MockManagedOutboundChannel) channelProvider.channelFor(Id.of(3));
    
    assertEquals(Message1, mock.writes.get(0));
    assertEquals(Message2, mock.writes.get(1));
    assertEquals(Message3, mock.writes.get(2));
  }
  
  @Before
  public void setUp() throws Exception {
    pool = new ByteBufferPool(10, 1024);
    channelProvider = new MockManagedOutboundChannelProvider(Id.of(1), config);
    outbound = new Outbound(channelProvider, new ByteBufferPool(10, 10_000));
  }
}
