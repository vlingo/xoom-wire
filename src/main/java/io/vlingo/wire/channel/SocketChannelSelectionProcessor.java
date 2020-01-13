// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.channel;

import java.nio.channels.SocketChannel;

import io.vlingo.actors.ActorInstantiator;
import io.vlingo.common.pool.ResourcePool;
import io.vlingo.wire.message.ConsumerByteBuffer;

public interface SocketChannelSelectionProcessor {
  void close();
  void process(final SocketChannel clientChannel);

  static class SocketChannelSelectionProcessorInstantiator implements ActorInstantiator<SocketChannelSelectionProcessorActor> {
    final RequestChannelConsumerProvider provider;
    final String name;
    final ResourcePool<ConsumerByteBuffer, Void> requestBufferPool;
    final long probeInterval;
    final long probeTimeout;

    public SocketChannelSelectionProcessorInstantiator(
            final RequestChannelConsumerProvider provider,
            final String name,
            final ResourcePool<ConsumerByteBuffer, Void> requestBufferPool,
            final long probeInterval,
            final long probeTimeout) {

      this.provider = provider;
      this.name = name;
      this.requestBufferPool = requestBufferPool;
      this.probeInterval = probeInterval;
      this.probeTimeout = probeTimeout;
    }

    @Override
    public SocketChannelSelectionProcessorActor instantiate() {
      return new SocketChannelSelectionProcessorActor(provider, name, requestBufferPool, probeInterval, probeTimeout);
    }

    @Override
    public Class<SocketChannelSelectionProcessorActor> type() {
      return SocketChannelSelectionProcessorActor.class;
    }
  }
}
