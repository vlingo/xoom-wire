// Copyright © 2012-2022 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.
package io.vlingo.xoom.wire.fdx.inbound.rsocket;

import io.rsocket.Closeable;
import io.rsocket.transport.ServerTransport;
import io.rsocket.transport.netty.server.TcpServerTransport;
import io.vlingo.xoom.actors.Logger;
import io.vlingo.xoom.wire.channel.ChannelReader;
import io.vlingo.xoom.wire.fdx.inbound.InboundChannelReaderProvider;

import java.util.function.Function;

public class RSocketInboundChannelReaderProvider implements InboundChannelReaderProvider {
  private final int maxMessageSize;
  private final Logger logger;
  private final Function<Integer, ServerTransport<? extends Closeable>> serverTransportProvider;

  /**
   * Build a instance of provider that will create {@link RSocketChannelInboundReader} using the default RSocket server transport, {@link TcpServerTransport}.
   *
   * @param maxMessageSize max message size for the inbound channel
   * @param logger logger to be used
   */
  public RSocketInboundChannelReaderProvider(final int maxMessageSize, final Logger logger) {
    this.maxMessageSize = maxMessageSize;
    this.logger = logger;
    this.serverTransportProvider = TcpServerTransport::create;
  }

  /**
   * Build a instance of provider that will create {@link RSocketChannelInboundReader} using a different RSocket server transport.
   *
   * @param maxMessageSize max message size for the inbound channel
   * @param logger logger to be used
   * @param serverTransportProvider function that given a port, returns a instance of {@link ServerTransport}
   */
  public RSocketInboundChannelReaderProvider(final int maxMessageSize, final Logger logger,
                                             final Function<Integer, ServerTransport<? extends Closeable>> serverTransportProvider) {
    this.maxMessageSize = maxMessageSize;
    this.logger = logger;
    this.serverTransportProvider = serverTransportProvider;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ChannelReader channelFor(final int port, final String name) {
    return new RSocketChannelInboundReader(this.serverTransportProvider.apply(port), port, name, this.maxMessageSize, this.logger);
  }
}
