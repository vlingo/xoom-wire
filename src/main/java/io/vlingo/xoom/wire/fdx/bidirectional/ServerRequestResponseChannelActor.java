// Copyright © 2012-2021 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.xoom.wire.fdx.bidirectional;

import io.vlingo.xoom.actors.Actor;
import io.vlingo.xoom.actors.Definition;
import io.vlingo.xoom.actors.Stoppable;
import io.vlingo.xoom.common.Cancellable;
import io.vlingo.xoom.common.Completes;
import io.vlingo.xoom.common.Scheduled;
import io.vlingo.xoom.common.pool.ElasticResourcePool;
import io.vlingo.xoom.common.pool.ResourcePool;
import io.vlingo.xoom.wire.channel.RefreshableSelector;
import io.vlingo.xoom.wire.channel.RequestChannelConsumerProvider;
import io.vlingo.xoom.wire.channel.SocketChannelSelectionProcessor;
import io.vlingo.xoom.wire.channel.SocketChannelSelectionProcessor.SocketChannelSelectionProcessorInstantiator;
import io.vlingo.xoom.wire.channel.SocketChannelSelectionProcessorActor;
import io.vlingo.xoom.wire.fdx.bidirectional.netty.server.NettyServerChannelActor;
import io.vlingo.xoom.wire.message.ConsumerByteBuffer;
import io.vlingo.xoom.wire.message.ConsumerByteBufferPool;

import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

/**
 * Deprecated. Use {@link NettyServerChannelActor}.
 * Will be removed in next release.
 */
@Deprecated
public class ServerRequestResponseChannelActor extends Actor implements ServerRequestResponseChannel, Scheduled<Object> {
  private final Cancellable cancellable;
  private final ServerSocketChannel channel;
  private final String name;
  private final SocketChannelSelectionProcessor[] processors;
  private final int port;
  private int processorPoolIndex;
  private final long probeTimeout;
  private final ConsumerByteBufferPool requestBufferPool;
  private final RefreshableSelector selector;

  @SuppressWarnings("unchecked")
  public ServerRequestResponseChannelActor(
          final RequestChannelConsumerProvider provider,
          final int port,
          final String name,
          final int processorPoolSize,
          final int maxBufferPoolSize,
          final int maxMessageSize,
          final long probeInterval,
          final long probeTimeout) {

    this.name = name;
    this.probeTimeout = probeTimeout;

    try {
      this.requestBufferPool = new ConsumerByteBufferPool(ElasticResourcePool.Config.of(maxBufferPoolSize), maxMessageSize);

      this.processors = startProcessors(provider, name, processorPoolSize, this.requestBufferPool, probeInterval, probeTimeout);

      this.port = port;
      logger().info(getClass().getSimpleName() + ": OPENING PORT: " + this.port);

      this.channel = ServerSocketChannel.open();
      this.selector = RefreshableSelector.open(name);
      channel.socket().bind(new InetSocketAddress(port));
      channel.configureBlocking(false);
      this.selector.registerWith(channel, SelectionKey.OP_ACCEPT);
    } catch (Exception e) {
      final String message = "Failure opening socket because: " + e.getMessage();
      logger().error(message, e);
      throw new IllegalArgumentException(message);
    }

    this.cancellable = stage().scheduler().schedule(selfAs(Scheduled.class), null, 100, probeInterval);
  }


  //=========================================
  // ServerRequestResponseChannel
  //=========================================

  @Override
  public void close() {
    if (isStopped()) return;

    selfAs(Stoppable.class).stop();
  }

  @Override
  public Completes<Integer> port() {
    return completes().with(this.port);
  }

  //=========================================
  // Scheduled
  //=========================================

  @Override
  public void intervalSignal(final Scheduled<Object> scheduled, final Object data) {
    probeChannel();
  }


  //=========================================
  // Stoppable
  //=========================================

  @Override
  public void stop() {
    cancellable.cancel();

    for (final SocketChannelSelectionProcessor processor : processors) {
      processor.close();
    }

    try {
      selector.close();
    } catch (Exception e) {
      logger().error("Failed to close selector for: '" + name + "'", e);
    }

    try {
      channel.close();
    } catch (Exception e) {
      logger().error("Failed to close channel for: '" + name + "'", e);
    }

    super.stop();
  }


  //=========================================
  // internal implementation
  //=========================================

  private void probeChannel() {
    if (isStopped()) return;

    try {
      final Iterator<SelectionKey> iterator = selector.select(probeTimeout);

      while (iterator.hasNext()) {
        final SelectionKey key = iterator.next();
        iterator.remove();

        if (key.isValid()) {
          if (key.isAcceptable()) {
            accept(key);
          }
        }
      }
    } catch (Exception e) {
      logger().error(getClass().getSimpleName() + ": Failed to accept client channel for '" + name + "' because: " + e.getMessage(), e);
    }
  }

  private void accept(final SelectionKey key) {
    final ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();

    try {
      if (serverChannel.isOpen()) {
        final SocketChannel clientChannel = serverChannel.accept();

        if (clientChannel != null) {
          clientChannel.configureBlocking(false);
          pooledProcessor().process(clientChannel);
        }
      }
    } catch (Exception e) {
      final String message = getClass().getSimpleName() + ": Failed to accept client socket for " + name + " because: " + e.getMessage();
      logger().error(message, e);
      throw new IllegalArgumentException(message);
    }
  }

  private SocketChannelSelectionProcessor pooledProcessor() {
    if (processorPoolIndex >= processors.length) {
      processorPoolIndex = 0;
    }
    return processors[processorPoolIndex++];
  }

  private SocketChannelSelectionProcessor[] startProcessors(
          final RequestChannelConsumerProvider provider,
          final String name,
          final int processorPoolSize,
          final ResourcePool<ConsumerByteBuffer, String> requestBufferPool,
          final long probeInterval,
          final long probeTimeout)
  throws Exception {

    final SocketChannelSelectionProcessor[] processors = new SocketChannelSelectionProcessor[processorPoolSize];

    try {
      for (int idx = 0; idx < processors.length; ++idx) {
        processors[idx] = childActorFor(
                SocketChannelSelectionProcessor.class,
                Definition.has(SocketChannelSelectionProcessorActor.class,
                        new SocketChannelSelectionProcessorInstantiator(provider, name + "-processor-" + idx, requestBufferPool, probeInterval, probeTimeout)));
      }
    } catch (Exception e) {
      logger().error(getClass().getSimpleName() + "FATAL: Socket channel processors cannot be started because: " + e.getMessage(), e);
      throw e;
    }

    return processors;
  }
}
