// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.
package io.vlingo.wire.fdx.bidirectional;

import io.vlingo.actors.World;
import io.vlingo.wire.channel.RequestChannelConsumerProvider;
import io.vlingo.wire.channel.ResponseChannelConsumer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public abstract class AbstractServerChannelActorTest {
  private static AtomicInteger TEST_PORT = new AtomicInteger(49560);

  protected static final int POOL_SIZE = 100;

  private ClientRequestResponseChannel client;
  private ServerRequestResponseChannel server;
  private World world;

  protected int testPort = TEST_PORT.incrementAndGet();

  protected abstract ClientRequestResponseChannel createClient(ResponseChannelConsumer consumer)
    throws Exception;

  protected abstract ServerRequestResponseChannel createServer(World world,
                                                               RequestChannelConsumerProvider consumerProvider);

  protected abstract void request(ClientRequestResponseChannel client, final String request);

  @Before
  public void setUp() {
    world = World.startWithDefaults("test-request-response-channel");
    // the client is created lazily
  }

  @Test
  public void testBasicRequestResponse()
    throws Exception {
    final String request = "Hello, Request-Response";

    TestRequestChannelConsumer serverConsumer = new TestRequestChannelConsumer(1, request.length());
    TestResponseChannelConsumer clientConsumer = new TestResponseChannelConsumer(1, request.length());
    client = createClient(clientConsumer);
    server = createServer(world, () -> serverConsumer);
    request(client, request);

    while (serverConsumer.remaining() > 0) {
      ;
    }

    while (clientConsumer.remaining() > 0) {
      client.probeChannel();
    }

    assertEquals(1, serverConsumer.consumeCount());
    assertEquals(1, clientConsumer.consumeCount());
    assertEquals(clientConsumer.responses().get(0), serverConsumer.requests().get(0));
  }

  @Test
  public void testGappyRequestResponse() throws Exception {
    final String requestPart1 = "Request Part-1";
    final String requestPart2 = ":Request Part-2";
    final String requestPart3 = ":Request Part-3";

    int expectedRequestLength = requestPart1.length() + requestPart2.length() + requestPart3.length();
    TestRequestChannelConsumer serverConsumer = new TestRequestChannelConsumer(1, expectedRequestLength);
    TestResponseChannelConsumer clientConsumer = new TestResponseChannelConsumer(1, expectedRequestLength);
    client = createClient(clientConsumer);
    server = createServer(world, () -> serverConsumer);

    // simulate network latency for parts of single request

    request(client, requestPart1);
    Thread.sleep(100);
    request(client, requestPart2);
    Thread.sleep(200);
    request(client, requestPart3);
    while (serverConsumer.remaining() > 0) {
      ;
    }

    while (clientConsumer.remaining() > 0) {
      Thread.sleep(10);
      client.probeChannel();
    }

    assertEquals(1, serverConsumer.consumeCount());
    assertEquals(1, clientConsumer.consumeCount());
    assertEquals(clientConsumer.responses().get(0), serverConsumer.requests().get(0));
  }


  @Test
  public void test10RequestResponse()
    throws Exception {
    final String request = "Hello, Request-Response";

    int numMessages = 10;
    int expectedRequestLength = request.length() + 1; // digits 0 - 9
    TestRequestChannelConsumer serverConsumer = new TestRequestChannelConsumer(numMessages, expectedRequestLength);
    TestResponseChannelConsumer clientConsumer = new TestResponseChannelConsumer(numMessages, expectedRequestLength);
    client = createClient(clientConsumer);
    server = createServer(world, () -> serverConsumer);

    for (int idx = 0; idx < numMessages; ++idx) {
      request(client, request + idx);
    }

    while (clientConsumer.remaining() > 0) {
      client.probeChannel();
    }

    assertEquals(numMessages, serverConsumer.consumeCount());
    assertEquals(numMessages, clientConsumer.consumeCount());

    List<String> requests = serverConsumer.requests();
    List<String> responses = clientConsumer.responses();
    for (int idx = 0; idx < numMessages; ++idx) {
      assertEquals(responses.get(idx), requests.get(idx));
    }
  }

  @Test
  public void testThatRequestResponsePoolLimitsNotExceeded()
    throws Exception {
    final int TOTAL = POOL_SIZE * 2;

    final String request = "Hello, Request-Response";

    int expectedRequestLength = request.length() + 3; // digits 000 - 999
    TestRequestChannelConsumer serverConsumer = new TestRequestChannelConsumer(TOTAL, expectedRequestLength);
    TestResponseChannelConsumer clientConsumer = new TestResponseChannelConsumer(TOTAL, expectedRequestLength);
    client = createClient(clientConsumer);
    server = createServer(world, () -> serverConsumer);

    for (int idx = 0; idx < TOTAL; ++idx) {
      request(client, request + String.format("%03d", idx));
    }

    while (clientConsumer.remaining() > 0) {
      client.probeChannel();
    }

    assertEquals(TOTAL, serverConsumer.consumeCount());
    assertEquals(TOTAL, clientConsumer.consumeCount());

    List<String> requests = serverConsumer.requests();
    List<String> responses = clientConsumer.responses();
    for (int idx = 0; idx < TOTAL; ++idx) {
      assertEquals(responses.get(idx), requests.get(idx));
    }
  }

  @After
  public void tearDown() {
    try {
      server.close();
    } catch (Exception e) {
      // ignore
    }
    try {
      client.close();
    } catch (Exception e) {
      // ignore
    }

    try { Thread.sleep(1000); } catch (Exception e) {  }

    try {
      world.terminate();
    } catch (Exception e) {
      // ignore
    }
  }

}