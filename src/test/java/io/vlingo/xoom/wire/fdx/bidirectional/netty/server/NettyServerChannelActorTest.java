// Copyright © 2012-2021 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.
package io.vlingo.xoom.wire.fdx.bidirectional.netty.server;

import java.util.concurrent.atomic.AtomicInteger;

import io.vlingo.xoom.actors.Definition;
import io.vlingo.xoom.wire.channel.RequestChannelConsumerProvider;
import io.vlingo.xoom.wire.fdx.bidirectional.BaseServerChannelTest;

public class NettyServerChannelActorTest extends BaseServerChannelTest {
  private static AtomicInteger TEST_PORT = new AtomicInteger(37370);

  @Override
  protected int getNextTestPort() {
    return TEST_PORT.incrementAndGet();
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