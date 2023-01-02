// Copyright © 2012-2023 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.xoom.wire.fdx.bidirectional;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Ignore;

import io.vlingo.xoom.actors.Definition;
import io.vlingo.xoom.wire.channel.RequestChannelConsumerProvider;
import io.vlingo.xoom.wire.fdx.bidirectional.ServerRequestResponseChannel.ServerRequestResponseChannelInstantiator;

@Ignore
public class SocketRequestResponseChannelTest extends BaseServerChannelTest {
  private static AtomicInteger TEST_PORT = new AtomicInteger(37370);

  @Override
  protected int getNextTestPort() {
    return TEST_PORT.incrementAndGet();
  }

  @SuppressWarnings("deprecation")
  @Override
  protected Definition getServerDefinition(final RequestChannelConsumerProvider provider, final int port, final int maxBufferPoolSize,
                                           final int maxMessageSize) {
    final ServerRequestResponseChannelInstantiator instantiator = new ServerRequestResponseChannelInstantiator(provider,
                                                                                                               port,
                                                                                                               "test-server",
                                                                                                               1,
                                                                                                               maxBufferPoolSize,
                                                                                                               maxMessageSize,
                                                                                                               10L,
                                                                                                               2L);
    return Definition.has(ServerRequestResponseChannelActor.class, instantiator);
  }

}
