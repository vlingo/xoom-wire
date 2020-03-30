// Copyright © 2012-2020 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.
package io.vlingo.wire.fdx.bidirectional.rsocket;

import io.rsocket.transport.ClientTransport;
import io.rsocket.transport.local.LocalClientTransport;
import io.rsocket.transport.local.LocalServerTransport;
import io.vlingo.actors.Definition;
import io.vlingo.actors.Logger;
import io.vlingo.wire.channel.RequestChannelConsumerProvider;
import io.vlingo.wire.fdx.bidirectional.BaseServerChannelTest;
import io.vlingo.wire.fdx.bidirectional.ClientRequestResponseChannel;
import io.vlingo.wire.fdx.bidirectional.TestResponseChannelConsumer;
import io.vlingo.wire.node.Address;
import io.vlingo.wire.node.AddressType;
import io.vlingo.wire.node.Host;

import java.nio.ByteBuffer;
import java.time.Duration;

public class RSocketServerChannelActorTest extends BaseServerChannelTest {
  private final LocalServerTransport serverTransport = LocalServerTransport.create("rsocket-fdx-server-test");
  private final ClientTransport clientTransport = LocalClientTransport.create("rsocket-fdx-server-test");

  @Override
  protected int getNextTestPort() {
    return 0;
  }

  @Override
  protected Definition getServerDefinition(final RequestChannelConsumerProvider provider, final int port, final int maxBufferPoolSize,
                                           final int maxMessageSize) {
    final RSocketServerChannelActor.Instantiator instantiator = new RSocketServerChannelActor.Instantiator(provider,
                                                                                                           serverTransport,
                                                                                                           0,
                                                                                                           "test-server",
                                                                                                           maxBufferPoolSize,
                                                                                                           maxMessageSize);

    return Definition.has(RSocketServerChannelActor.class, instantiator);
  }

  @Override
  protected ClientRequestResponseChannel getClient(final Logger logger, final int testPort, final TestResponseChannelConsumer clientConsumer,
                                                   final int maxBufferPoolSize, final int maxMessageSize) throws Exception {
    final Integer serverPort = server.port().<Integer>await();

    return new RSocketClientChannel(clientTransport,
                                    Address.from(Host.of("127.0.0.1"), serverPort, AddressType.NONE),
                                    clientConsumer,
                                    maxBufferPoolSize,
                                    maxMessageSize,
                                    logger,
                                    Duration.ofSeconds(1));
  }

  @Override
  protected void request(final String request) {
    client.requestWith(ByteBuffer.wrap(request.getBytes()));
  }

}