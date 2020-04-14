// Copyright © 2012-2020 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.
package io.vlingo.wire.fdx.bidirectional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.nio.ByteBuffer;
import java.time.Duration;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import io.vlingo.actors.Definition;
import io.vlingo.actors.Logger;
import io.vlingo.actors.World;
import io.vlingo.actors.testkit.TestUntil;
import io.vlingo.wire.BaseWireTest;
import io.vlingo.wire.channel.RequestChannelConsumerProvider;
import io.vlingo.wire.fdx.bidirectional.netty.client.NettyClientRequestResponseChannel;
import io.vlingo.wire.message.ByteBufferAllocator;
import io.vlingo.wire.node.Address;
import io.vlingo.wire.node.AddressType;
import io.vlingo.wire.node.Host;

public abstract class BaseServerChannelTest extends BaseWireTest {
  private static final int POOL_SIZE = 100;

  private ByteBuffer buffer;
  private TestResponseChannelConsumer clientConsumer;
  private Logger logger;
  private TestRequestChannelConsumerProvider provider;
  private TestRequestChannelConsumer serverConsumer;
  private World world;
  protected ClientRequestResponseChannel client;
  protected ServerRequestResponseChannel server;
  protected int testPort;

  @Test
  public void testBasicRequestResponse() throws Exception {
    final String request = "Hello, Request-Response";

    serverConsumer.currentExpectedRequestLength = request.length();
    clientConsumer.currentExpectedResponseLength = serverConsumer.currentExpectedRequestLength;
    serverConsumer.untilConsume = TestUntil.happenings(1);
    clientConsumer.untilConsume = TestUntil.happenings(1);

    request(request);

    while (serverConsumer.untilConsume.remaining() > 0) {
      ;
    }
    serverConsumer.untilConsume.completes();

    while (clientConsumer.untilConsume.remaining() > 0) {
      client.probeChannel();
    }
    clientConsumer.untilConsume.completes();

    assertFalse(serverConsumer.requests.isEmpty());
    assertEquals(1, serverConsumer.consumeCount);
    assertEquals(serverConsumer.consumeCount, serverConsumer.requests.size());

    assertFalse(clientConsumer.responses.isEmpty());
    assertEquals(1, clientConsumer.consumeCount);
    assertEquals(clientConsumer.consumeCount, clientConsumer.responses.size());

    assertEquals(clientConsumer.responses.get(0), serverConsumer.requests.get(0));
  }

  @Test
  public void testGappyRequestResponse() throws Exception {
    final String requestPart1 = "Request Part-1";
    final String requestPart2 = ":Request Part-2";
    final String requestPart3 = ":Request Part-3";

    serverConsumer.currentExpectedRequestLength = requestPart1.length() + requestPart2.length() + requestPart3.length();
    clientConsumer.currentExpectedResponseLength = serverConsumer.currentExpectedRequestLength;

    // simulate network latency for parts of single request
    serverConsumer.untilConsume = TestUntil.happenings(1);
    clientConsumer.untilConsume = TestUntil.happenings(1);

    request(requestPart1);
    Thread.sleep(100);
    request(requestPart2);
    Thread.sleep(200);
    request(requestPart3);
    while (serverConsumer.untilConsume.remaining() > 0) {
      ;
    }
    serverConsumer.untilConsume.completes();

    while (clientConsumer.untilConsume.remaining() > 0) {
      Thread.sleep(10);
      client.probeChannel();
    }
    clientConsumer.untilConsume.completes();

    assertFalse(serverConsumer.requests.isEmpty());
    assertEquals(1, serverConsumer.consumeCount);
    assertEquals(serverConsumer.consumeCount, serverConsumer.requests.size());

    assertFalse(clientConsumer.responses.isEmpty());
    assertEquals(1, clientConsumer.consumeCount);
    assertEquals(clientConsumer.consumeCount, clientConsumer.responses.size());

    assertEquals(clientConsumer.responses.get(0), serverConsumer.requests.get(0));
  }

  @Test
  public void test10RequestResponse() throws Exception {
    final String request = "Hello, Request-Response";

    serverConsumer.currentExpectedRequestLength = request.length() + 1; // digits 0 - 9
    clientConsumer.currentExpectedResponseLength = serverConsumer.currentExpectedRequestLength;

    serverConsumer.untilConsume = TestUntil.happenings(10);
    clientConsumer.untilConsume = TestUntil.happenings(10);

    for (int idx = 0; idx < 10; ++idx) {
      request(request + idx);
    }

    while (clientConsumer.untilConsume.remaining() > 0) {
      client.probeChannel();
    }

    serverConsumer.untilConsume.completes();
    clientConsumer.untilConsume.completes();

    assertFalse(serverConsumer.requests.isEmpty());
    assertEquals(10, serverConsumer.consumeCount);
    assertEquals(serverConsumer.consumeCount, serverConsumer.requests.size());

    assertFalse(clientConsumer.responses.isEmpty());
    assertEquals(10, clientConsumer.consumeCount);
    assertEquals(clientConsumer.consumeCount, clientConsumer.responses.size());

    for (int idx = 0; idx < 10; ++idx) {
      assertEquals(clientConsumer.responses.get(idx), serverConsumer.requests.get(idx));
    }
  }

  @Test
  public void testThatRequestResponsePoolLimitsNotExceeded() throws Exception {
    final int TOTAL = POOL_SIZE * 2;

    final String request = "Hello, Request-Response";

    serverConsumer.currentExpectedRequestLength = request.length() + 3; // digits 000 - 999
    clientConsumer.currentExpectedResponseLength = serverConsumer.currentExpectedRequestLength;

    serverConsumer.untilConsume = TestUntil.happenings(TOTAL);
    clientConsumer.untilConsume = TestUntil.happenings(TOTAL);

    for (int idx = 0; idx < TOTAL; ++idx) {
      request(request + String.format("%03d", idx));
    }

    while (clientConsumer.untilConsume.remaining() > 0) {
      client.probeChannel();
    }
    serverConsumer.untilConsume.completes();
    clientConsumer.untilConsume.completes();

    assertFalse(serverConsumer.requests.isEmpty());
    assertEquals(TOTAL, serverConsumer.consumeCount);
    assertEquals(serverConsumer.consumeCount, serverConsumer.requests.size());

    assertFalse(clientConsumer.responses.isEmpty());
    assertEquals(TOTAL, clientConsumer.consumeCount);
    assertEquals(clientConsumer.consumeCount, clientConsumer.responses.size());

    for (int idx = 0; idx < TOTAL; ++idx) {
      assertEquals(clientConsumer.responses.get(idx), serverConsumer.requests.get(idx));
    }
  }

  @Test
  public void testServerStops() throws InterruptedException {
    this.server.stop();

    Thread.sleep(300);

    Assert.assertTrue(this.server.isStopped());
  }

  protected void request(final String request) throws Exception {
    buffer.clear();
    buffer.put(request.getBytes());
    buffer.flip();
    client.requestWith(buffer);
  }

  @Before
  public final void setUp() throws Exception {
    world = World.startWithDefaults("test-request-response-channel");

    buffer = ByteBufferAllocator.allocate(1024);
    logger = Logger.basicLogger();
    provider = new TestRequestChannelConsumerProvider();
    serverConsumer = (TestRequestChannelConsumer) provider.consumer;

    testPort = getNextTestPort();

    final Definition definition = getServerDefinition(provider, testPort, POOL_SIZE, 10240);
    server = world.actorFor(ServerRequestResponseChannel.class, definition);

    clientConsumer = new TestResponseChannelConsumer();

    client = getClient(logger, testPort, clientConsumer, POOL_SIZE, 10240);
  }

  @After
  public void tearDown() {
    server.close();
    client.close();

    try {
      Thread.sleep(1000);
    } catch (Exception e) {
    }

    world.terminate();
  }

  protected abstract int getNextTestPort();

  protected abstract Definition getServerDefinition(final RequestChannelConsumerProvider provider, final int port, final int maxBufferPoolSize,
                                                    final int maxMessageSize);

  protected ClientRequestResponseChannel getClient(final Logger logger, final int testPort, final TestResponseChannelConsumer clientConsumer,
                                                   final int maxBufferPoolSize, final int maxMessageSize) throws Exception {

    return new NettyClientRequestResponseChannel(Address.from(Host.of("localhost"), testPort, AddressType.NONE),
                                                 clientConsumer,
                                                 maxBufferPoolSize,
                                                 maxMessageSize,
                                                 Duration.ofMillis(1000),
                                                 Duration.ZERO,
                                                 Duration.ZERO);
  }

}
