// Copyright © 2012-2021 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.xoom.wire.fdx.bidirectional;

import io.vlingo.xoom.actors.ActorInstantiator;
import io.vlingo.xoom.actors.Address;
import io.vlingo.xoom.actors.Definition;
import io.vlingo.xoom.actors.Stage;
import io.vlingo.xoom.actors.Stoppable;
import io.vlingo.xoom.common.Completes;
import io.vlingo.xoom.wire.channel.RequestChannelConsumerProvider;
import io.vlingo.xoom.wire.fdx.bidirectional.netty.server.NettyServerChannelActor;

public interface ServerRequestResponseChannel extends Stoppable {
  static ServerRequestResponseChannel start(
          final Stage stage,
          final RequestChannelConsumerProvider provider,
          final int port,
          final String name,
          final int processorPoolSize,
          final int maxBufferPoolSize,
          final int maxMessageSize,
          final long probeInterval,
          final long probeTimeout) {

    final ActorInstantiator<NettyServerChannelActor> instantiator =
            new NettyServerChannelActor.Instantiator(provider, port, name, processorPoolSize, maxBufferPoolSize, maxMessageSize);

    final ServerRequestResponseChannel channel =
            stage.actorFor(
              ServerRequestResponseChannel.class,
              Definition.has(instantiator.type(), instantiator));

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
          final long probeInterval,
          final long probeTimeout) {

    final ActorInstantiator<NettyServerChannelActor> instantiator =
            new NettyServerChannelActor.Instantiator(provider, port, name, processorPoolSize, maxBufferPoolSize, maxMessageSize);

    final ServerRequestResponseChannel channel =
            stage.actorFor(
              ServerRequestResponseChannel.class,
              Definition.has(instantiator.type(), instantiator, mailboxName, address.name()),
              address,
              stage.world().defaultLogger());

    return channel;
  }

  void close();

  @SuppressWarnings("deprecation")
  static class ServerRequestResponseChannelInstantiator implements ActorInstantiator<ServerRequestResponseChannelActor> {
    private static final long serialVersionUID = -198611338719466278L;

    private final RequestChannelConsumerProvider provider;
    private final int port;
    private final String name;
    private final int processorPoolSize;
    private final int maxBufferPoolSize;
    private final int maxMessageSize;
    private final long probeInterval;
    private final long probeTimeout;

    public ServerRequestResponseChannelInstantiator(
            final RequestChannelConsumerProvider provider,
            final int port,
            final String name,
            final int processorPoolSize,
            final int maxBufferPoolSize,
            final int maxMessageSize,
            final long probeInterval,
            final long probeTimeout) {

      this.provider = provider;
      this.port = port;
      this.name = name;
      this.processorPoolSize = processorPoolSize;
      this.maxBufferPoolSize = maxBufferPoolSize;
      this.maxMessageSize = maxMessageSize;
      this.probeInterval = probeInterval;
      this.probeTimeout = probeTimeout;
    }

    @Override
    public ServerRequestResponseChannelActor instantiate() {
      return new ServerRequestResponseChannelActor(provider, port, name, processorPoolSize, maxBufferPoolSize, maxMessageSize, probeInterval, probeTimeout);
    }

    @Override
    public Class<ServerRequestResponseChannelActor> type() {
      return ServerRequestResponseChannelActor.class;
    }
  }


  Completes<Integer> port();
}
