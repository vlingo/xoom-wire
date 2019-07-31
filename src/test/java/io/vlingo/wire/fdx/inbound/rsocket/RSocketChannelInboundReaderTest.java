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
import io.vlingo.wire.node.Address;
import io.vlingo.wire.node.Host;
import io.vlingo.wire.node.Id;
import io.vlingo.wire.node.Name;
import io.vlingo.wire.node.Node;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.function.BiConsumer;

import static org.junit.Assert.assertEquals;

public class RSocketChannelInboundReaderTest {
  private static final String AppMessage = "APP TEST ";
  private static final String OpMessage = "OP TEST ";
  private static final Logger logger = Logger.basicLogger();
  private static Node node = Node.with(Id.of(2), Name.of("node2"), Host.of("localhost"), 37477, 37478);

  @Test
  public void testInvalidMessageContinueConsuming() throws IOException {
    testInboundOutbound(node.operationalAddress(), (channelReader, outboundChannel) -> {
      final MockChannelReaderConsumer consumer = new MockChannelReaderConsumer();
      final int nrOfMessages = 10;
      consumer.afterCompleting(nrOfMessages);

      try {
        channelReader.openFor(consumer);
      } catch (IOException e) {
        Assert.fail(e.getMessage());
      }

      //Write a simple string, not created using RawMessage
      outboundChannel.write(ByteBuffer.wrap(UUID.randomUUID().toString().getBytes()));

      //Write a normal message, built using RawMessage
      for (int i = 0; i < nrOfMessages; i++) {
        final ByteBuffer buffer = ByteBufferAllocator.allocate(1024);

        final String message = UUID.randomUUID().toString() + "_" + i;
        final RawMessage rawMessage = RawMessage.from(0, 0, message);
        outboundChannel.write(rawMessage.asByteBuffer(buffer));
      }

      assertEquals(nrOfMessages, consumer.getConsumeCount());
    });
  }

  @Test
  public void testOpInboundChannel() throws Exception {
    testInboundOutbound(node.operationalAddress(), (channelReader, outboundChannel) -> {
      final MockChannelReaderConsumer consumer = new MockChannelReaderConsumer();
      final int nrOfMessages = 100;
      consumer.afterCompleting(nrOfMessages);

      try {
        channelReader.openFor(consumer);
      } catch (IOException e) {
        Assert.fail(e.getMessage());
      }

      for (int i = 0; i < nrOfMessages; i++) {
        final ByteBuffer buffer = ByteBufferAllocator.allocate(1024);

        final String message1 = OpMessage + i;
        final RawMessage rawMessage1 = RawMessage.from(0, 0, message1);
        outboundChannel.write(rawMessage1.asByteBuffer(buffer));
      }

      //test the nr of consumed messages
      assertEquals(nrOfMessages, consumer.getConsumeCount());

      //test the order of delivery
      for (int i = 0; i < nrOfMessages; i++) {
        assertEquals(OpMessage + i, consumer.getMessage(i));
      }
    });
  }

  @Test
  public void testAppInboundChannel() throws Exception {
    testInboundOutbound(node.applicationAddress(), (channelReader, outboundChannel) -> {
      final MockChannelReaderConsumer consumer = new MockChannelReaderConsumer();
      final int nrOfMessages = 100;
      consumer.afterCompleting(nrOfMessages);

      try {
        channelReader.openFor(consumer);
      } catch (IOException e) {
        Assert.fail(e.getMessage());
      }

      for (int i = 0; i < nrOfMessages; i++) {
        final ByteBuffer buffer = ByteBufferAllocator.allocate(1024);

        final String message1 = AppMessage + i;
        final RawMessage rawMessage1 = RawMessage.from(0, 0, message1);
        outboundChannel.write(rawMessage1.asByteBuffer(buffer));
      }

      //test the nr of consumed messages
      assertEquals(nrOfMessages, consumer.getConsumeCount());

      //test the order of delivery
      for (int i = 0; i < nrOfMessages; i++) {
        assertEquals(AppMessage + i, consumer.getMessage(i));
      }
    });
  }

  private static void testInboundOutbound(final Address address, BiConsumer<ChannelReader, ManagedOutboundChannel> consumer){
    final RSocketOutboundChannel outbound = new RSocketOutboundChannel(node, address, logger);
    final RSocketChannelInboundReader inbound = new RSocketChannelInboundReader(address.port(), "test" + address.port(), 1024, logger);
    try {
      consumer.accept(inbound, outbound);
    } finally {
      outbound.close();
      inbound.close();
    }
  }
}