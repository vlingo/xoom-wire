// Copyright © 2012-2020 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.fdx.bidirectional;

import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.vlingo.actors.World;
import io.vlingo.actors.testkit.AccessSafely;
import io.vlingo.wire.BaseWireTest;
import io.vlingo.wire.message.ByteBufferAllocator;
import io.vlingo.wire.node.Address;
import io.vlingo.wire.node.AddressType;
import io.vlingo.wire.node.Host;

public class SecureClientRequestResponseChannelTest extends BaseWireTest {
  private static final int POOL_SIZE = 100;

  private ClientRequestResponseChannel client;
  private TestSecureResponseChannelConsumer clientConsumer;
  private World world;

  @Test
  public void testThatSecureClientRequestResponse() throws Exception {
    System.out.println("testThatSecureClientRequestResponse()");

    final Address address = Address.from(Host.of("www.google.com"), 443, AddressType.NONE);
    client = new SecureClientRequestResponseChannel(address, clientConsumer, POOL_SIZE, 65536, world.defaultLogger());

    clientConsumer.currentExpectedResponseLength = 500;
    final AccessSafely access = clientConsumer.afterCompleting(1);

    final String get = "GET / HTTP/1.1\nHost: www.google.com\nConnection: close\n\n";
    final ByteBuffer buffer = ByteBufferAllocator.allocate(1000);
    buffer.put(get.getBytes());
    buffer.flip();
    client.requestWith(buffer);

    for (int count = 0; count < 100; ++count) {
      if (access.totalWrites() > 0) break;
      client.probeChannel();
      Thread.sleep(100);
    }

    assertTrue(clientConsumer.consumeCount() > 0);
    assertTrue(clientConsumer.responses().get(0).contains("google.com"));
    System.out.println("\nRESULT: " + clientConsumer.responses().get(0));
  }

  @Before
  public void setUp() throws Exception {
    world = World.startWithDefaults("test-request-response-channel");

    clientConsumer = new TestSecureResponseChannelConsumer();
  }

  @After
  public void tearDown() {
    client.close();

    world.terminate();
  }
}
