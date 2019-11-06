// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.fdx.bidirectional;

import io.vlingo.actors.Address;
import io.vlingo.actors.Definition;
import io.vlingo.actors.Stage;
import io.vlingo.actors.Stoppable;
import io.vlingo.common.Completes;
import io.vlingo.wire.channel.RequestChannelConsumerProvider;

import java.util.List;

public interface ServerRequestResponseChannel extends Stoppable {
  static ServerRequestResponseChannel start(
          final Stage stage,
          final RequestChannelConsumerProvider provider,
          final int port,
          final String name,
          final int processorPoolSize,
          final int maxBufferPoolSize,
          final int maxMessageSize,
          final long probeInterval) {

    final List<Object> params = Definition.parameters(provider, port, name, processorPoolSize, maxBufferPoolSize, maxMessageSize, probeInterval);

    final ServerRequestResponseChannel channel =
            stage.actorFor(
              ServerRequestResponseChannel.class,
              Definition.has(ServerRequestResponseChannelActor.class, params));

    return channel;
  }

  static ServerRequestResponseChannel start(
          final Stage stage,
          final Address address,
          final String mailboxName,
          final RequestChannelConsumerProvider provider,
          final int port,
          final String name,
          final int processorPoolSize,
          final int maxBufferPoolSize,
          final int maxMessageSize,
          final long probeInterval) {

    final List<Object> params = Definition.parameters(provider, port, name, processorPoolSize, maxBufferPoolSize, maxMessageSize, probeInterval);

    final ServerRequestResponseChannel channel =
            stage.actorFor(
              ServerRequestResponseChannel.class,
              Definition.has(ServerRequestResponseChannelActor.class, params, mailboxName, address.name()),
              address,
              stage.world().defaultLogger());

    return channel;
  }

  void close();

  Completes<Integer> port();
}
