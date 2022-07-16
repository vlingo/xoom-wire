// Copyright © 2012-2022 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.xoom.wire.fdx.outbound;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vlingo.xoom.common.pool.ResourcePool;
import io.vlingo.xoom.wire.message.ConsumerByteBuffer;
import io.vlingo.xoom.wire.message.RawMessage;
import io.vlingo.xoom.wire.node.Id;
import io.vlingo.xoom.wire.node.Node;


public class Outbound {

  @SuppressWarnings("unused")
  private final Logger logger = LoggerFactory.getLogger(Outbound.class);

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

  public void open(final Node node) {
    provider.channelFor(node);
  }

  public void sendTo(final RawMessage message, final Node targetNode) {
    sendTo(bytesFrom(message, pool.acquire("Outbound#sendTo")), targetNode);
  }

  public void sendTo(final ConsumerByteBuffer buffer, final Node targetNode) {
    open(targetNode);
    provider.channelFor(targetNode).writeAsync(buffer.asByteBuffer())
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
