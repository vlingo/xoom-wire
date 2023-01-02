// Copyright © 2012-2023 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.xoom.wire.fdx.inbound.tcp;

import io.vlingo.xoom.actors.Logger;
import io.vlingo.xoom.actors.testkit.AccessSafely;
import io.vlingo.xoom.wire.channel.ChannelReader;
import io.vlingo.xoom.wire.channel.MockChannelReaderConsumer;
import io.vlingo.xoom.wire.fdx.outbound.tcp.ManagedOutboundSocketChannel;
import io.vlingo.xoom.wire.message.AbstractMessageTool;
import io.vlingo.xoom.wire.message.ByteBufferAllocator;
import io.vlingo.xoom.wire.message.RawMessage;
import io.vlingo.xoom.wire.node.Host;
import io.vlingo.xoom.wire.node.Id;
import io.vlingo.xoom.wire.node.Name;
import io.vlingo.xoom.wire.node.Node;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.ByteBuffer;

import static org.junit.Assert.assertEquals;

public class SocketChannelInboundReaderTest extends AbstractMessageTool {
  private static final String AppMessage = "APP TEST ";
  private static final String OpMessage = "OP TEST ";
  
  private ManagedOutboundSocketChannel appChannel;
  private ChannelReader appReader;
  private ManagedOutboundSocketChannel opChannel;
  private ChannelReader opReader;
  private Node node;
  
  @Test
  public void testOpInboundChannel() throws Exception {
    final MockChannelReaderConsumer consumer = new MockChannelReaderConsumer();
    final AccessSafely consumerAccess = consumer.afterCompleting(0);
    
    opReader.openFor(consumer);
    
    final ByteBuffer buffer = ByteBufferAllocator.allocate(1024);
    
    final String message1 = OpMessage + 1;
    final RawMessage rawMessage1 = RawMessage.from(0, 0, message1);
    opChannel.write(rawMessage1.asByteBuffer(buffer));
    
    probeUntilConsumed(opReader, consumerAccess);
    
    assertEquals(1, (int)consumerAccess.readFrom("consumeCount"));
    assertEquals(message1, consumerAccess.readFrom("message", 0));

    final String message2 = OpMessage + 2;
    final RawMessage rawMessage2 = RawMessage.from(0, 0, message2);
    opChannel.write(rawMessage2.asByteBuffer(buffer));
    
    probeUntilConsumed(opReader, consumerAccess);
    
    assertEquals(2, (int)consumerAccess.readFrom("consumeCount"));
    assertEquals(message2, consumerAccess.readFrom("message", 1));
  }
  
  @Test
  public void testAppInboundChannel() throws Exception {
    final MockChannelReaderConsumer consumer = new MockChannelReaderConsumer();
    final AccessSafely consumerAccess = consumer.afterCompleting(0);
    
    appReader.openFor(consumer);
    
    final ByteBuffer buffer = ByteBufferAllocator.allocate(1024);
    
    final String message1 = AppMessage + 1;
    final RawMessage rawMessage1 = RawMessage.from(0, 0, message1);
    appChannel.write(rawMessage1.asByteBuffer(buffer));
    
    probeUntilConsumed(appReader, consumerAccess);
    
    assertEquals(1, (int)consumerAccess.readFrom("consumeCount"));
    assertEquals(message1, consumerAccess.readFrom("message", 0));

    final String message2 = AppMessage + 2;
    final RawMessage rawMessage2 = RawMessage.from(0, 0, message2);
    appChannel.write(rawMessage2.asByteBuffer(buffer));
    
    probeUntilConsumed(appReader, consumerAccess);
    
    assertEquals(2, (int)consumerAccess.readFrom("consumeCount"));
    assertEquals(message2, consumerAccess.readFrom("message", 1));
  }
  
  @Before
  public void setUp() throws Exception {
    node = Node.with(Id.of(2), Name.of("node2"), Host.of("localhost"), 37373, 37374);
    final Logger logger = Logger.basicLogger();
    opChannel = new ManagedOutboundSocketChannel(node, node.operationalAddress(), logger);
    appChannel = new ManagedOutboundSocketChannel(node, node.applicationAddress(), logger);
    opReader = new SocketChannelInboundReader(node.operationalAddress().port(), "test-op", 1024, logger);
    appReader = new SocketChannelInboundReader(node.applicationAddress().port(), "test-app", 1024, logger);
  }
  
  @After
  public void tearDown() {
    opChannel.close();
    appChannel.close();
    opReader.close();
    appReader.close();
  }

  private void probeUntilConsumed(final ChannelReader reader, final AccessSafely consumerAccess ) {
    final int currentConsumedCount = consumerAccess.readFrom("consumeCount");
    
    for (int idx = 0; idx < 100; ++idx) {
      reader.probeChannel();
      
      if ((int)consumerAccess.readFrom("consumeCount") > currentConsumedCount) break;
    }
  }
}
