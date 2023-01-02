// Copyright © 2012-2023 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.xoom.wire.fdx.bidirectional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.nio.ByteBuffer;
import java.time.Duration;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import io.vlingo.xoom.actors.Definition;
import io.vlingo.xoom.actors.Logger;
import io.vlingo.xoom.actors.World;
import io.vlingo.xoom.wire.BaseWireTest;
import io.vlingo.xoom.wire.channel.RequestChannelConsumerProvider;
import io.vlingo.xoom.wire.fdx.bidirectional.TestRequestChannelConsumer.State;
import io.vlingo.xoom.wire.fdx.bidirectional.netty.client.NettyClientRequestResponseChannel;
import io.vlingo.xoom.wire.message.ByteBufferAllocator;
import io.vlingo.xoom.wire.node.Address;
import io.vlingo.xoom.wire.node.AddressType;
import io.vlingo.xoom.wire.node.Host;

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
    serverConsumer.state = new State(1);
    // TODO: add a generalized State class
    clientConsumer.state = new TestResponseChannelConsumer.State(1);

    request(request);
    int remaining = clientConsumer.state.access.readFrom("remaining");
    while (remaining != 0) {
      client.probeChannel();
      remaining = clientConsumer.state.access.readFrom("remaining");
    }

    assertFalse(serverConsumer.requests.isEmpty());
    assertEquals(1, (int) serverConsumer.state.access.readFrom("consumeCount"));
    assertEquals((int) serverConsumer.state.access.readFrom("consumeCount"), serverConsumer.requests.size());

    assertFalse(clientConsumer.responses.isEmpty());
    assertEquals(1, (int) clientConsumer.state.access.readFrom("consumeCount"));
    assertEquals((int) clientConsumer.state.access.readFrom("consumeCount"), clientConsumer.responses.size());

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
    serverConsumer.state = new TestRequestChannelConsumer.State(1);
    clientConsumer.state = new TestResponseChannelConsumer.State(1);

    request(requestPart1);
    Thread.sleep(100);
    request(requestPart2);
    Thread.sleep(200);
    request(requestPart3);

    int remaining = clientConsumer.state.access.readFrom("remaining");
    while (remaining != 0) {
      client.probeChannel();
      remaining = clientConsumer.state.access.readFrom("remaining");
    }

    assertFalse(serverConsumer.requests.isEmpty());
    assertEquals(1, (int) serverConsumer.state.access.readFrom("consumeCount"));
    assertEquals((int) serverConsumer.state.access.readFrom("consumeCount"), serverConsumer.requests.size());

    assertFalse(clientConsumer.responses.isEmpty());
    assertEquals(1, (int) clientConsumer.state.access.readFrom("consumeCount"));
    assertEquals((int) clientConsumer.state.access.readFrom("consumeCount"), clientConsumer.responses.size());

    assertEquals(clientConsumer.responses.get(0), serverConsumer.requests.get(0));
  }

  @Test
  public void test10RequestResponse() throws Exception {
    final String request = "Hello, Request-Response";

    serverConsumer.currentExpectedRequestLength = request.length() + 1; // digits 0 - 9
    clientConsumer.currentExpectedResponseLength = serverConsumer.currentExpectedRequestLength;

    serverConsumer.state = new TestRequestChannelConsumer.State(10);
    clientConsumer.state = new TestResponseChannelConsumer.State(10);

    for (int idx = 0; idx < 10; ++idx) {
      request(request + idx);
    }

    int remaining = clientConsumer.state.access.readFrom("remaining");
    while (remaining != 0) {
      client.probeChannel();
      remaining = clientConsumer.state.access.readFrom("remaining");
    }

    assertFalse(serverConsumer.requests.isEmpty());
    assertEquals(10, (int) serverConsumer.state.access.readFrom("consumeCount"));
    assertEquals((int) serverConsumer.state.access.readFrom("consumeCount"), serverConsumer.requests.size());

    assertFalse(clientConsumer.responses.isEmpty());
    assertEquals(10, (int) clientConsumer.state.access.readFrom("consumeCount"));
    assertEquals((int) clientConsumer.state.access.readFrom("consumeCount"), clientConsumer.responses.size());

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

    serverConsumer.state = new TestRequestChannelConsumer.State(TOTAL);
    clientConsumer.state = new TestResponseChannelConsumer.State(TOTAL);

    for (int idx = 0; idx < TOTAL; ++idx) {
      request(request + String.format("%03d", idx));
    }

    int remaining = clientConsumer.state.access.readFrom("remaining");
    while (remaining != 0) {
      client.probeChannel();
      remaining = clientConsumer.state.access.readFrom("remaining");
    }

    assertFalse(serverConsumer.requests.isEmpty());
    assertEquals(TOTAL, (int) serverConsumer.state.access.readFrom("consumeCount"));
    assertEquals((int) serverConsumer.state.access.readFrom("consumeCount"), serverConsumer.requests.size());

    assertFalse(clientConsumer.responses.isEmpty());
    assertEquals(TOTAL, (int) clientConsumer.state.access.readFrom("consumeCount"));
    assertEquals((int) clientConsumer.state.access.readFrom("consumeCount"), clientConsumer.responses.size());

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
