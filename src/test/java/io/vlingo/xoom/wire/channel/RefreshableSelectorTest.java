// Copyright © 2012-2022 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.xoom.wire.channel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.vlingo.xoom.actors.World;
import io.vlingo.xoom.actors.testkit.AccessSafely;
import io.vlingo.xoom.wire.fdx.inbound.tcp.SocketChannelInboundReader;
import io.vlingo.xoom.wire.message.ByteBufferAllocator;
import io.vlingo.xoom.wire.message.RawMessage;
import io.vlingo.xoom.wire.node.Host;
import io.vlingo.xoom.wire.node.Id;
import io.vlingo.xoom.wire.node.Name;
import io.vlingo.xoom.wire.node.Node;

public class RefreshableSelectorTest {
  private static int RefreshCountThreshold = 10;
  private static AtomicInteger TEST_PORT = new AtomicInteger(20200);
  private static final String TestMessage = "TEST ";
  private static int TotalMessages = 10000;

  private SocketChannelWriter channelWriter;
  private SocketChannelInboundReader channelReader;
  private World world;

  @Test
  public void testRefreshSelector() throws Exception {
    System.out.println("testRefreshSelector");
    final MockChannelReaderConsumer consumer = new MockChannelReaderConsumer();

    channelReader.openFor(consumer);

    final ByteBuffer buffer = ByteBufferAllocator.allocate(1024);

    int total = 0;

    for (int count = 1; count <= TotalMessages; ++count) {
      final String message1 = TestMessage + count;
      final RawMessage rawMessage1 = RawMessage.from(0, 0, message1);
      channelWriter.write(rawMessage1, buffer);

      final AccessSafely consumerAccess1 = consumer.afterCompleting(0);
      probeUntilConsumed(channelReader, consumerAccess1);

      final int consumerCount1 = consumerAccess1.readFrom("consumeCount");
      final String consumerMessage1 = consumerAccess1.readFrom("message", total);

      assertEquals(++total, consumerCount1);
      assertEquals(message1, consumerMessage1);

      final String message2 = TestMessage + count;
      final RawMessage rawMessage2 = RawMessage.from(0, 0, message2);
      channelWriter.write(rawMessage2, buffer);

      final AccessSafely consumerAccess2 = consumer.afterCompleting(0);
      probeUntilConsumed(channelReader, consumerAccess2);

      final int consumerCount2 = consumerAccess1.readFrom("consumeCount");
      final String consumerMessage2 = consumerAccess1.readFrom("message", total);

      assertEquals(++total, consumerCount2);
      assertEquals(message2, consumerMessage2);
    }

    final long lessThanActualRefreshes = TotalMessages / RefreshCountThreshold;
    assertTrue(channelReader.__test__only_Selector().refreshedCount() >= lessThanActualRefreshes);
  }

  @Before
  public void setUp() throws Exception {
    world = World.startWithDefaults("test-refreshable-selector");

    RefreshableSelector.resetForTest();
    RefreshableSelector.withCountedThreshold(RefreshCountThreshold, world.defaultLogger());

    final int operationalPort = TEST_PORT.incrementAndGet();
    final int applicationPort = TEST_PORT.incrementAndGet();

    final Node node = Node.with(Id.of(2), Name.of("node2"), Host.of("localhost"), operationalPort, applicationPort);
    channelWriter = new SocketChannelWriter(node.operationalAddress(), world.defaultLogger());
    channelReader = new SocketChannelInboundReader(node.operationalAddress().port(), "test-reader", 1024, world.defaultLogger());
  }

  @After
  public void tearDown() {
    channelWriter.close();
    channelReader.close();
    world.terminate();
  }

  private void probeUntilConsumed(final SocketChannelInboundReader reader, final AccessSafely consumerAccess) {
    final int previousConsumedCount = consumerAccess.readFrom("consumeCount");

    for (int idx = 0; idx < 100; ++idx) {
      reader.probeChannel();

      final int currentConsumedCount = consumerAccess.readFrom("consumeCount");

      if (currentConsumedCount > previousConsumedCount) {
        break;
      }
    }
  }
}
