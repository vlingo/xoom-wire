// Copyright © 2012-2020 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.fdx.bidirectional;

import io.vlingo.actors.ActorInstantiator;
import io.vlingo.actors.Address;
import io.vlingo.actors.Definition;
import io.vlingo.actors.Stage;
import io.vlingo.actors.Stoppable;
import io.vlingo.wire.channel.RequestChannelConsumerProvider;
import io.vlingo.wire.fdx.bidirectional.rsocket.RSocketServerChannelActor;

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

    final ServerRequestResponseChannelInstantiator instantiator =
            new ServerRequestResponseChannelInstantiator(provider, port, name, processorPoolSize, maxBufferPoolSize, maxMessageSize, probeInterval, probeTimeout);

    final ServerRequestResponseChannel channel =
            stage.actorFor(
              ServerRequestResponseChannel.class,
              Definition.has(ServerRequestResponseChannelActor.class, instantiator));

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

    final ServerRequestResponseChannelInstantiator instantiator =
            new ServerRequestResponseChannelInstantiator(provider, port, name, processorPoolSize, maxBufferPoolSize, maxMessageSize, probeInterval, probeTimeout);

    final ServerRequestResponseChannel channel =
            stage.actorFor(
              ServerRequestResponseChannel.class,
              Definition.has(ServerRequestResponseChannelActor.class, instantiator, mailboxName, address.name()),
              address,
              stage.world().defaultLogger());

    return channel;
  }

  void close();

  static class ServerRequestResponseChannelInstantiator implements ActorInstantiator<ServerRequestResponseChannelActor> {
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

  static class RSocketServerRequestResponseChannelInstantiator implements ActorInstantiator<RSocketServerChannelActor> {
    private final RequestChannelConsumerProvider provider;
    private final int port;
    private final String name;
    private final int maxBufferPoolSize;
    private final int messageBufferSize;

    public RSocketServerRequestResponseChannelInstantiator(
            final RequestChannelConsumerProvider provider,
            final int port,
            final String name,
            final int maxBufferPoolSize,
            final int messageBufferSize) {

      this.provider = provider;
      this.port = port;
      this.name = name;
      this.maxBufferPoolSize = maxBufferPoolSize;
      this.messageBufferSize = messageBufferSize;
    }

    @Override
    public RSocketServerChannelActor instantiate() {
      return new RSocketServerChannelActor(provider, port, name, maxBufferPoolSize, messageBufferSize);
    }

    @Override
    public Class<RSocketServerChannelActor> type() {
      return RSocketServerChannelActor.class;
    }
  }
}
