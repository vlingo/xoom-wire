// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.fdx.bidirectional;

import io.vlingo.actors.Logger;
import io.vlingo.actors.World;
import io.vlingo.wire.channel.RequestChannelConsumerProvider;
import io.vlingo.wire.channel.ResponseChannelConsumer;
import io.vlingo.wire.message.ByteBufferAllocator;
import io.vlingo.wire.node.Address;
import io.vlingo.wire.node.AddressType;
import io.vlingo.wire.node.Host;

import java.nio.ByteBuffer;

public class SocketRequestResponseChannelTest extends AbstractServerChannelActorTest {

  private ByteBuffer buffer = ByteBufferAllocator.allocate(1024);

  @Override
  protected ClientRequestResponseChannel createClient(ResponseChannelConsumer consumer)
    throws Exception {
    final Logger logger = Logger.basicLogger();
    return new BasicClientRequestResponseChannel(Address.from(Host.of("localhost"), testPort,  AddressType.NONE),
      consumer, POOL_SIZE, 10240, logger);
  }

  @Override
  protected ServerRequestResponseChannel createServer(World world, RequestChannelConsumerProvider consumerProvider) {
    return ServerRequestResponseChannel.start(
      world.stage(),
      consumerProvider,
      testPort,
      "test-server",
      1,
      POOL_SIZE,
      10240,
      10L);
  }

  protected void request(ClientRequestResponseChannel client, final String request) {
    buffer.clear();
    buffer.put(request.getBytes());
    buffer.flip();
    client.requestWith(buffer);
  }
}
