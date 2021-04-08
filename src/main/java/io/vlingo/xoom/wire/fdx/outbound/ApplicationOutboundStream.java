// Copyright © 2012-2020 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.xoom.wire.fdx.outbound;

import io.vlingo.xoom.actors.ActorInstantiator;
import io.vlingo.xoom.actors.Definition;
import io.vlingo.xoom.actors.Stage;
import io.vlingo.xoom.actors.Stoppable;
import io.vlingo.xoom.common.pool.ResourcePool;
import io.vlingo.xoom.wire.message.ConsumerByteBuffer;
import io.vlingo.xoom.wire.message.RawMessage;
import io.vlingo.xoom.wire.node.Id;

public interface ApplicationOutboundStream extends Stoppable {
  public static ApplicationOutboundStream instance(
          final Stage stage,
          final ManagedOutboundChannelProvider provider,
          final ResourcePool<ConsumerByteBuffer, String> byteBufferPool) {

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
    private static final long serialVersionUID = 3996997791426073111L;

    private final ResourcePool<ConsumerByteBuffer, String> byteBufferPool;
    private final ManagedOutboundChannelProvider provider;

    public ApplicationOutboundStreamInstantiator(
            final ManagedOutboundChannelProvider provider,
            final ResourcePool<ConsumerByteBuffer, String> byteBufferPool) {
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
