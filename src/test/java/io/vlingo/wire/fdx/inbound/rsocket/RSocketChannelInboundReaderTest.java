// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.
package io.vlingo.wire.fdx.inbound.rsocket;

import io.vlingo.actors.Logger;
import io.vlingo.wire.channel.MockChannelReaderConsumer;
import io.vlingo.wire.fdx.outbound.ManagedOutboundChannel;
import io.vlingo.wire.fdx.outbound.rsocket.RSocketOutboundChannel;
import io.vlingo.wire.message.ByteBufferAllocator;
import io.vlingo.wire.message.RawMessage;
import io.vlingo.wire.node.Address;
import io.vlingo.wire.node.AddressType;
import io.vlingo.wire.node.Host;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.UUID;
import java.util.function.BiConsumer;

import static org.junit.Assert.assertEquals;

public class RSocketChannelInboundReaderTest {
  private static final String AppMessage = "APP TEST ";
  private static final String OpMessage = "OP TEST ";
  private static final Logger logger = Logger.basicLogger();

  @Test
  public void testInvalidMessageContinueConsuming() throws Exception {
    testInboundOutbound(AddressType.OP, (consumer, outboundChannel) -> {
      final int nrOfMessages = 10;
      consumer.afterCompleting(nrOfMessages);

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
    testInboundOutbound(AddressType.OP, (consumer, outboundChannel) -> {
      final int nrOfMessages = 100;
      consumer.afterCompleting(nrOfMessages);
      
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
    testInboundOutbound(AddressType.APP, (consumer, outboundChannel) -> {
      final int nrOfMessages = 100;
      consumer.afterCompleting(nrOfMessages);
      
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
  
  private static void testInboundOutbound(final AddressType addressType, BiConsumer<MockChannelReaderConsumer, ManagedOutboundChannel> consumer) throws InterruptedException {
    final RSocketChannelInboundReader channelReader = new RSocketChannelInboundReader(0, "testInboundReader", 1024, logger);
    Thread.sleep(200); //give some time for the inbound to initialize
    final MockChannelReaderConsumer channelConsumer = new MockChannelReaderConsumer();
    try {
      channelReader.openFor(channelConsumer);
    } catch (Throwable e) {
      Assert.fail(e.getMessage());
    }

    final Address address = new Address(Host.of("127.0.0.1"), channelReader.port(), addressType);

    final RSocketOutboundChannel outbound = new RSocketOutboundChannel(address, Duration.ofMillis(200), logger);
    try {
      consumer.accept(channelConsumer, outbound);
    } finally {
      outbound.close();
      channelReader.close();
    }
  }
}