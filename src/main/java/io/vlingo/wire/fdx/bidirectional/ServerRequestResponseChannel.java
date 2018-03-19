// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.fdx.bidirectional;

import java.util.List;

import io.vlingo.actors.Definition;
import io.vlingo.actors.Stage;
import io.vlingo.actors.Stoppable;
import io.vlingo.wire.channel.RequestChannelConsumer;
import io.vlingo.wire.channel.ResponseSenderChannel;

public interface ServerRequestResponseChannel extends ResponseSenderChannel, Stoppable {
  static ServerRequestResponseChannel start(
          final Stage stage,
          final RequestChannelConsumer consumer,
          final int port,
          final String name,
          final int maxBufferPoolSize,
          final int maxMessageSize,
          final long probeTimeout,
          final long probeInterval) {

    final List<Object> params = Definition.parameters(consumer, port, name, maxBufferPoolSize, maxMessageSize, probeTimeout, probeInterval);

    final ServerRequestResponseChannel channel =
            stage.actorFor(
              Definition.has(ServerRequestResponseChannelActor.class, params),
              ServerRequestResponseChannel.class);

    return channel;
  }

  void close();
}
