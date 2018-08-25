// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.fdx.bidirectional;

import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.util.Iterator;

import io.vlingo.actors.Actor;
import io.vlingo.actors.Cancellable;
import io.vlingo.actors.Definition;
import io.vlingo.actors.Scheduled;
import io.vlingo.actors.Stoppable;
import io.vlingo.wire.channel.RequestChannelConsumerProvider;
import io.vlingo.wire.channel.SocketChannelSelectionProcessor;
import io.vlingo.wire.channel.SocketChannelSelectionProcessorActor;

public class ServerRequestResponseChannelActor extends Actor implements ServerRequestResponseChannel, Scheduled {
  private final Cancellable cancellable;
  private final ServerSocketChannel channel;
  private final String name;
  private final long probeTimeout;
  private final SocketChannelSelectionProcessor[] processors;
  private int processorPoolIndex;
  private final Selector selector;

  public ServerRequestResponseChannelActor(
          final RequestChannelConsumerProvider provider,
          final int port,
          final String name,
          final int processorPoolSize,
          final int maxBufferPoolSize,
          final int maxMessageSize,
          final long probeTimeout,
          final long probeInterval) {

    this.name = name;
    this.probeTimeout = probeTimeout;
    this.processors = startProcessors(provider, name, processorPoolSize, maxBufferPoolSize, maxMessageSize, probeTimeout, probeInterval);

    try {
      this.channel = ServerSocketChannel.open();
      this.selector = Selector.open();
      channel.socket().bind(new InetSocketAddress(port));
      channel.configureBlocking(false);
      channel.register(selector, SelectionKey.OP_ACCEPT);
    } catch (Exception e) {
      final String message = "Failure opening socket because: " + e.getMessage();
      logger().log(message, e);
      throw new IllegalArgumentException(message);
    }

    this.cancellable = stage().scheduler().schedule(selfAs(Scheduled.class), null, 100, probeInterval);
  }


  //=========================================
  // Scheduled
  //=========================================

  @Override
  public void close() {
    if (isStopped()) return;

    selfAs(Stoppable.class).stop();
  }

  //=========================================
  // Scheduled
  //=========================================

  @Override
  public void intervalSignal(final Scheduled scheduled, final Object data) {
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
      logger().log("Failed to close selctor for: '" + name + "'", e);
    }

    try {
      channel.close();
    } catch (Exception e) {
      logger().log("Failed to close channel for: '" + name + "'", e);
    }

    super.stop();
  }


  //=========================================
  // internal implementation
  //=========================================

  private void probeChannel() {
    if (isStopped()) return;

    try {
      if (selector.select(probeTimeout) > 0) {
        final Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();

        while (iterator.hasNext()) {
          final SelectionKey key = iterator.next();
          iterator.remove();

          if (key.isValid()) {
            if (key.isAcceptable()) {
              accept(key);
            }
          }
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
      logger().log("Failed to accept client channel for '" + name + "' because: " + e.getMessage(), e);
    }
  }

  private void accept(final SelectionKey key) {
    pooledProcessor().process(key);
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
          final int maxBufferPoolSize,
          final int maxMessageSize,
          final long probeTimeout,
          final long probeInterval) {

    final SocketChannelSelectionProcessor[] processors = new SocketChannelSelectionProcessor[processorPoolSize];

    for (int idx = 0; idx < processors.length; ++idx) {
      processors[idx] = childActorFor(
              Definition.has(SocketChannelSelectionProcessorActor.class,
                      Definition.parameters(provider, name + "-processor-" + idx, maxBufferPoolSize, maxMessageSize, probeTimeout, probeInterval)),
              SocketChannelSelectionProcessor.class);
    }

    return processors;
  }
}
