// Copyright © 2012-2020 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.channel;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.vlingo.actors.Logger;
import io.vlingo.actors.World;
import io.vlingo.actors.testkit.TestUntil;
import io.vlingo.wire.fdx.bidirectional.BasicClientRequestResponseChannel;
import io.vlingo.wire.fdx.bidirectional.ClientRequestResponseChannel;
import io.vlingo.wire.fdx.bidirectional.ServerRequestResponseChannel;
import io.vlingo.wire.fdx.bidirectional.TestRequestChannelConsumer;
import io.vlingo.wire.fdx.bidirectional.TestRequestChannelConsumerProvider;
import io.vlingo.wire.fdx.bidirectional.TestResponseChannelConsumer;
import io.vlingo.wire.message.ByteBufferAllocator;
import io.vlingo.wire.node.Address;
import io.vlingo.wire.node.AddressType;
import io.vlingo.wire.node.Host;

public class RefreshableSelectorTest {
  private static final int POOL_SIZE = 100;
  private static AtomicInteger TEST_PORT = new AtomicInteger(37370);

  private ByteBuffer buffer;
  private ClientRequestResponseChannel client;
  private TestResponseChannelConsumer clientConsumer;
  private TestRequestChannelConsumerProvider provider;
  private ServerRequestResponseChannel server;
  private TestRequestChannelConsumer serverConsumer;
  private World world;

  @Test
  public void testBasicRequestResponse() throws Exception {
    final String requestTemplate = "Hello, Refresh ";

    for (int count = 0; count < 30; ++count) {
      final String request = requestTemplate + count;

      serverConsumer.currentExpectedRequestLength = request.length();
      clientConsumer.currentExpectedResponseLength = serverConsumer.currentExpectedRequestLength;
      request(request);

      serverConsumer.untilConsume = TestUntil.happenings(1);
      clientConsumer.untilConsume = TestUntil.happenings(1);

      while (serverConsumer.untilConsume.remaining() > 0) {
        ;
      }
      serverConsumer.untilConsume.completes();

      while (clientConsumer.untilConsume.remaining() > 0) {
        client.probeChannel();
      }
      clientConsumer.untilConsume.completes();
    }
  }

  @Before
  public void setUp() throws Exception {
    RefreshableSelector.withCountedThreshold(10, Logger.basicLogger());

    world = World.startWithDefaults("test-refreshable-selector");

    buffer = ByteBufferAllocator.allocate(1024);
    final Logger logger = Logger.basicLogger();
    provider = new TestRequestChannelConsumerProvider();
    serverConsumer = (TestRequestChannelConsumer) provider.consumer;

    final int testPort = TEST_PORT.incrementAndGet();

    server = ServerRequestResponseChannel.start(
                    world.stage(),
                    provider,
                    testPort,
                    "test-server",
                    1,
                    POOL_SIZE,
                    10240,
                    10L,
                    2L);

    clientConsumer = new TestResponseChannelConsumer();

    client = new BasicClientRequestResponseChannel(Address.from(Host.of("localhost"), testPort,  AddressType.NONE), clientConsumer, POOL_SIZE, 10240, logger);
  }

  @After
  public void tearDown() {
    server.close();
    client.close();

    try { Thread.sleep(1000); } catch (Exception e) {  }

    world.terminate();
  }

  private void request(final String request) {
    buffer.clear();
    buffer.put(request.getBytes());
    buffer.flip();
    client.requestWith(buffer);
  }
}
