// Copyright © 2012-2020 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.fdx.outbound;

import io.vlingo.common.pool.ResourcePool;
import io.vlingo.wire.message.ConsumerByteBuffer;
import io.vlingo.wire.message.RawMessage;
import io.vlingo.wire.node.Id;
import io.vlingo.wire.node.Node;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;


public class Outbound {
  private final ResourcePool<ConsumerByteBuffer, String> pool;
  private final ManagedOutboundChannelProvider provider;

  public Outbound(
      final ManagedOutboundChannelProvider provider,
      final ResourcePool<ConsumerByteBuffer, String> byteBufferPool) {

    this.provider = provider;
    this.pool = byteBufferPool;
  }

  public void broadcast(final RawMessage message) {
    broadcast(bytesFrom(message, pool.acquire("Outbound#broadcast")));
  }

  public void broadcast(final ConsumerByteBuffer buffer) {
    // currently based on configured nodes,
    // but eventually could be live-node based
    broadcast(provider.allOtherNodeChannels(), buffer);
  }

  public void broadcast(final Collection<Node> selectNodes, final RawMessage message) {
    broadcast(selectNodes, bytesFrom(message, pool.acquire("Outbound#broadcast")));
  }

  public void broadcast(final Collection<Node> selectNodes, final ConsumerByteBuffer buffer) {
    broadcast(provider.channelsFor(selectNodes), buffer);
  }

  public ConsumerByteBuffer bytesFrom(final RawMessage message, final ConsumerByteBuffer buffer) {
    message.copyBytesTo(buffer.clear().asByteBuffer());
    return buffer.flip();
  }

  public void close() {
    provider.close();
  }

  public void close(final Id id) {
    provider.close(id);
  }

  public ConsumerByteBuffer lendByteBuffer() {
    return pool.acquire("Outbound#lendByteBuffer");
  }

  public void open(final Id id) {
    provider.channelFor(id);
  }

  public void sendTo(final RawMessage message, final Id id) {
    sendTo(bytesFrom(message, pool.acquire("Outbound#sendTo")), id);
  }

  public void sendTo(final ConsumerByteBuffer buffer, final Id id) {
    open(id);
    provider.channelFor(id).writeAsync(buffer.asByteBuffer())
        .andFinallyConsume((aVoid) -> buffer.release());
  }

  private void broadcast(final Map<Id, ManagedOutboundChannel> channels, final ConsumerByteBuffer buffer) {
    AtomicInteger latch = new AtomicInteger(channels.size());
    channels.values()
        .forEach((channel) ->
            channel.writeAsync(
                // wrap the backing byte array into a read only ByteBuffer so that
                // each thread gets its own position to read from.
                ByteBuffer.wrap(buffer.array(), buffer.position(), buffer.limit())
                    .asReadOnlyBuffer()
                    .order(buffer.order()))
                .andFinallyConsume((ignored) -> {
                  if (latch.decrementAndGet() == 0) {
                    buffer.release();
                  }
                }));
  }
}
