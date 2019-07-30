// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.
package io.vlingo.wire.fdx.inbound.rsocket;

import io.vlingo.actors.Logger;
import io.vlingo.wire.channel.ChannelReader;
import io.vlingo.wire.channel.MockChannelReaderConsumer;
import io.vlingo.wire.fdx.outbound.ManagedOutboundChannel;
import io.vlingo.wire.fdx.outbound.rsocket.RSocketOutboundChannel;
import io.vlingo.wire.message.ByteBufferAllocator;
import io.vlingo.wire.message.RawMessage;
import io.vlingo.wire.node.Host;
import io.vlingo.wire.node.Id;
import io.vlingo.wire.node.Name;
import io.vlingo.wire.node.Node;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.ByteBuffer;

import static org.junit.Assert.assertEquals;

public class RSocketChannelInboundReaderTest {
  private static final String AppMessage = "APP TEST ";
  private static final String OpMessage = "OP TEST ";

  private ManagedOutboundChannel appChannel;
  private ChannelReader appReader;
  private ManagedOutboundChannel opChannel;
  private ChannelReader opReader;
  private Node node;

  @Test
  public void testOpInboundChannel() throws Exception {
    final MockChannelReaderConsumer consumer = new MockChannelReaderConsumer();
    final int nrOfMessages = 100;
    consumer.afterCompleting(nrOfMessages);

    opReader.openFor(consumer);

    for (int i = 0; i < nrOfMessages; i++) {
      final ByteBuffer buffer = ByteBufferAllocator.allocate(1024);

      final String message1 = OpMessage + i;
      final RawMessage rawMessage1 = RawMessage.from(0, 0, message1);
      opChannel.write(rawMessage1.asByteBuffer(buffer));
    }

    //test the nr of consumed messages
    assertEquals(nrOfMessages, consumer.getConsumeCount());

    //test the order of delivery
    for (int i = 0; i < nrOfMessages; i++) {
      assertEquals(OpMessage + i, consumer.getMessage(i));
    }
  }

  @Test
  public void testAppInboundChannel() throws Exception {
    final MockChannelReaderConsumer consumer = new MockChannelReaderConsumer();
    final int nrOfMessages = 100;
    consumer.afterCompleting(nrOfMessages);

    appReader.openFor(consumer);

    for (int i = 0; i < nrOfMessages; i++) {
      final ByteBuffer buffer = ByteBufferAllocator.allocate(1024);

      final String message1 = AppMessage + i;
      final RawMessage rawMessage1 = RawMessage.from(0, 0, message1);
      appChannel.write(rawMessage1.asByteBuffer(buffer));
    }

    //test the nr of consumed messages
    assertEquals(nrOfMessages, consumer.getConsumeCount());

    //test the order of delivery
    for (int i = 0; i < nrOfMessages; i++) {
      assertEquals(AppMessage + i, consumer.getMessage(i));
    }
  }

  @Before
  public void setUp() {
    node = Node.with(Id.of(2), Name.of("node2"), Host.of("localhost"), 37477, 37478);
    final Logger logger = Logger.basicLogger();
    opChannel = new RSocketOutboundChannel(node, node.operationalAddress(), logger);
    appChannel = new RSocketOutboundChannel(node, node.applicationAddress(), logger);
    opReader = new RSocketChannelInboundReader(node.operationalAddress().port(), "test-op", 1024, logger);
    appReader = new RSocketChannelInboundReader(node.applicationAddress().port(), "test-app", 1024, logger);
  }

  @After
  public void tearDown() {
    opChannel.close();
    appChannel.close();
    opReader.close();
    appReader.close();
  }

}