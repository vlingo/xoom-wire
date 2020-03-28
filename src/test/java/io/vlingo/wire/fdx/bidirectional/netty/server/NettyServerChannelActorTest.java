// Copyright © 2012-2020 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.
package io.vlingo.wire.fdx.bidirectional.netty.server;

import io.vlingo.actors.Definition;
import io.vlingo.wire.channel.RequestChannelConsumerProvider;
import io.vlingo.wire.fdx.bidirectional.BaseServerChannelTest;

import java.util.concurrent.atomic.AtomicInteger;

public class NettyServerChannelActorTest extends BaseServerChannelTest {
  private static AtomicInteger TEST_PORT = new AtomicInteger(37370);

  @Override
  protected int getNextTestPort() {
    return TEST_PORT.getAndIncrement();
  }

  @Override
  protected Definition getServerDefinition(final RequestChannelConsumerProvider provider, final int port, final int maxBufferPoolSize, final int maxMessageSize) {
    final NettyServerChannelActor.Instantiator instantiator = new NettyServerChannelActor.Instantiator(provider,
                                                                                                       port,
                                                                                                       "netty-test-server",
                                                                                                       1, maxBufferPoolSize,
                                                                                                       maxMessageSize);
    return Definition.has(NettyServerChannelActor.class, instantiator);
  }
}