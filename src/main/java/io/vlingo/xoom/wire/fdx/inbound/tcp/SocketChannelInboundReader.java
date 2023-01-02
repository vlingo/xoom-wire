// Copyright © 2012-2023 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.xoom.wire.fdx.inbound.tcp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

import io.vlingo.xoom.actors.Logger;
import io.vlingo.xoom.wire.channel.ChannelMessageDispatcher;
import io.vlingo.xoom.wire.channel.ChannelReader;
import io.vlingo.xoom.wire.channel.ChannelReaderConsumer;
import io.vlingo.xoom.wire.channel.RefreshableSelector;
import io.vlingo.xoom.wire.channel.SocketChannelSelectionReader;
import io.vlingo.xoom.wire.message.RawMessageBuilder;

public class SocketChannelInboundReader implements ChannelReader, ChannelMessageDispatcher {
  private final ServerSocketChannel channel;
  private boolean closed;
  private ChannelReaderConsumer consumer;
  private final Logger logger;
  private final int maxMessageSize;
  private final String name;
  private final int port;
  private final RefreshableSelector selector;

  public SocketChannelInboundReader(
          final int port,
          final String name,
          final int maxMessageSize,
          final Logger logger)
  throws Exception {
    this.port = port;
    this.name = name;
    this.channel = ServerSocketChannel.open();
    this.maxMessageSize = maxMessageSize;
    this.logger = logger;
    this.selector = RefreshableSelector.open(name);
  }

  //=========================================
  // InboundReader
  //=========================================

  @Override
  public void close() {
    if (closed) return;

    closed = true;

    try {
      selector.close();
    } catch (Exception e) {
      logger.error("Failed to close selector for: '" + name + "'", e);
    }

    try {
      channel.close();
    } catch (Exception e) {
      logger.error("Failed to close channel for: '" + name + "'", e);
    }
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public int port() {
    return this.port;
  }

  @Override
  public void openFor(final ChannelReaderConsumer consumer) throws IOException {
    if (closed) return; // for some tests it's possible to receive close() before start()

    this.consumer = consumer;
    logger().debug(getClass().getSimpleName() + ": OPENING PORT: " + port);
    channel.socket().bind(new InetSocketAddress(port));
    channel.configureBlocking(false);
    selector.registerWith(channel, SelectionKey.OP_ACCEPT);
  }

  @Override
  public void probeChannel() {
    if (closed) return;

    try {
      final Iterator<SelectionKey> iterator = selector.selectNow();

      while (iterator.hasNext()) {
        final SelectionKey key = iterator.next();
        iterator.remove();

        if (key.isValid()) {
          if (key.isAcceptable()) {
            accept(key);
          } else if (key.isReadable()) {
            new SocketChannelSelectionReader(this, key).read();
          }
        }
      }
    } catch (IOException e) {
      logger.error("Failed to read channel selector for: '" + name + "'", e);
    }
  }


  //=========================================
  // ChannelMessageDispatcher
  //=========================================

  @Override
  public ChannelReaderConsumer consumer() {
    return consumer;
  }

  @Override
  public Logger logger() {
    return logger;
  }

  // public String name(); is implemented above by InboundReader

  //=========================================
  // internal implementation
  //=========================================

  public RefreshableSelector __test__only_Selector() {
    return selector;
  }

  private void accept(final SelectionKey key) throws IOException {
    final ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();

    if (serverChannel.isOpen()) {
      final SocketChannel clientChannel = serverChannel.accept();

      clientChannel.configureBlocking(false);

      final SelectionKey clientChannelKey = selector.registerWith(clientChannel, SelectionKey.OP_READ);

      clientChannelKey.attach(new RawMessageBuilder(maxMessageSize));
    }
  }
}
