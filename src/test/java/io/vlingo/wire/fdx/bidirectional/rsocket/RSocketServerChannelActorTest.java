// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.
package io.vlingo.wire.fdx.bidirectional.rsocket;

import io.vlingo.actors.Definition;
import io.vlingo.actors.Logger;
import io.vlingo.actors.World;
import io.vlingo.wire.channel.RequestChannelConsumerProvider;
import io.vlingo.wire.channel.ResponseChannelConsumer;
import io.vlingo.wire.fdx.bidirectional.AbstractServerChannelActorTest;
import io.vlingo.wire.fdx.bidirectional.ClientRequestResponseChannel;
import io.vlingo.wire.fdx.bidirectional.ServerRequestResponseChannel;
import io.vlingo.wire.node.Address;
import io.vlingo.wire.node.AddressType;
import io.vlingo.wire.node.Host;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;

public class RSocketServerChannelActorTest extends AbstractServerChannelActorTest {

  protected ClientRequestResponseChannel createClient(ResponseChannelConsumer consumer) {
    final Logger logger = Logger.basicLogger();
    return new RSocketClientChannel(Address.from(Host.of("127.0.0.1"), testPort, AddressType.NONE),
      consumer, POOL_SIZE, 10240, logger, Duration.ofSeconds(1));
  }

  protected ServerRequestResponseChannel createServer(World world, RequestChannelConsumerProvider consumerProvider) {
    final List<Object> params = Definition.parameters(consumerProvider, testPort, "test-server",  POOL_SIZE, 10240);
    return world.actorFor(
      ServerRequestResponseChannel.class,
      Definition.has(RSocketServerChannelActor.class, params));
  }

  protected void request(ClientRequestResponseChannel client, final String request) {
    client.requestWith(ByteBuffer.wrap(request.getBytes()));
  }

}