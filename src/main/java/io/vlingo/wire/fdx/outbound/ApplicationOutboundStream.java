// Copyright © 2012-2020 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.fdx.outbound;

import io.vlingo.actors.ActorInstantiator;
import io.vlingo.actors.Definition;
import io.vlingo.actors.Stage;
import io.vlingo.actors.Stoppable;
import io.vlingo.wire.message.ConsumerByteBufferPool;
import io.vlingo.wire.message.RawMessage;
import io.vlingo.wire.node.Id;

public interface ApplicationOutboundStream extends Stoppable {
  public static ApplicationOutboundStream instance(
          final Stage stage,
          final ManagedOutboundChannelProvider provider,
          final ConsumerByteBufferPool byteBufferPool) {

    final Definition definition =
            Definition.has(
                    ApplicationOutboundStreamActor.class,
                    new ApplicationOutboundStreamInstantiator(provider, byteBufferPool),
                    "application-outbound-stream");

    final ApplicationOutboundStream applicationOutboundStream =
            stage.actorFor(ApplicationOutboundStream.class, definition);

    return applicationOutboundStream;
  }

  void broadcast(final RawMessage message);
  void sendTo(final RawMessage message, final Id targetId);

  static class ApplicationOutboundStreamInstantiator implements ActorInstantiator<ApplicationOutboundStreamActor> {
    private final ConsumerByteBufferPool byteBufferPool;
    private final ManagedOutboundChannelProvider provider;

    public ApplicationOutboundStreamInstantiator(
            final ManagedOutboundChannelProvider provider,
            final ConsumerByteBufferPool byteBufferPool) {
      this.provider = provider;
      this.byteBufferPool = byteBufferPool;
    }

    @Override
    public ApplicationOutboundStreamActor instantiate() {
      return new ApplicationOutboundStreamActor(provider, byteBufferPool);
    }

    @Override
    public Class<ApplicationOutboundStreamActor> type() {
      return ApplicationOutboundStreamActor.class;
    }
  }
}
