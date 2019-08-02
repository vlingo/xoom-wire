// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.
package io.vlingo.wire.fdx.outbound.rsocket;

import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.RSocketFactory;
import io.rsocket.frame.decoder.PayloadDecoder;
import io.rsocket.transport.netty.client.TcpClientTransport;
import io.rsocket.util.DefaultPayload;
import io.vlingo.actors.Logger;
import io.vlingo.wire.fdx.outbound.ManagedOutboundChannel;
import io.vlingo.wire.node.Address;
import io.vlingo.wire.node.Node;

import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.time.Duration;
import java.util.Optional;

public class RSocketOutboundChannel implements ManagedOutboundChannel {
  private final Node node;
  private final Logger logger;
  private final RSocketFactory.Start<RSocket> socketFactory;
  private RSocket clientSocket;
  private final long connectionRetries;
  private final Duration connectionRetryBackoff;

  public RSocketOutboundChannel(final Node node, final Address address, final Logger logger) {
    this(node, address, 10, Duration.ofSeconds(1), logger);
  }

  public RSocketOutboundChannel(final Node node, final Address address, long connectionRetries, Duration connectionRetryBackoff, final Logger logger) {
    this.node = node;
    this.logger = logger;
    this.connectionRetries = connectionRetries;
    this.connectionRetryBackoff = connectionRetryBackoff;

    this.socketFactory = RSocketFactory.connect()
                                       .frameDecoder(PayloadDecoder.ZERO_COPY)
                                       .transport(TcpClientTransport.create(address.hostName(), address.port()));
  }

  @Override
  public void close() {
    if (this.clientSocket != null && !this.clientSocket.isDisposed()) {
      try {
        this.clientSocket.dispose();
      } catch (final Throwable t) {
        logger.error("Unexpected error on closing client socket");
      }
    }
  }

  @Override
  public void write(final ByteBuffer buffer) {
    prepareSocket().ifPresent(rSocket -> {
      if (!rSocket.isDisposed()) {
        //Copy original buffer data because payload might not be sent immediately.
        ByteBuffer data = ByteBuffer.allocate(buffer.capacity());
        data.put(buffer);
        data.flip();

        final Payload payload = DefaultPayload.create(data);
        rSocket.fireAndForget(payload)
               .doOnError(throwable -> {
                 if (throwable instanceof ClosedChannelException) {
                   //close outbound channel
                   rSocket.dispose();
                   logger.error("Connection with {} closed", node, throwable);
                 } else {
                   logger.error("Failed write to node {}, because: {}", node, throwable.getMessage(), throwable);
                 }
               })
               .subscribe();
      }
    });
  }

  private Optional<RSocket> prepareSocket() {
    if (this.clientSocket == null) {
      try {
        this.clientSocket = socketFactory.start()
                                         .retryBackoff(connectionRetries, connectionRetryBackoff)
                                         .block();
      } catch (final Exception e) {
        logger.error("Failed to connect to {}", this.node);
        return Optional.empty();
      }
    }

    return Optional.ofNullable(this.clientSocket);
  }
}
