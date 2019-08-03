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
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.time.Duration;
import java.util.Optional;

public class RSocketOutboundChannel implements ManagedOutboundChannel {
  private final Address address;
  private final Logger logger;
  private RSocket clientSocket;
  private final long connectionRetries;
  private final Duration connectionRetryBackoff;

  public RSocketOutboundChannel(final Node node, final Address address, final Logger logger) {
    this(node, address, 10, Duration.ofSeconds(1), logger);
  }

  public RSocketOutboundChannel(final Node node, final Address address, long connectionRetries, Duration connectionRetryBackoff, final Logger logger) {
    this.address = address;
    this.logger = logger;
    this.connectionRetries = connectionRetries;
    this.connectionRetryBackoff = connectionRetryBackoff;
  }

  @Override
  public void close() {
    if (this.clientSocket != null && !this.clientSocket.isDisposed()) {
      try {
        this.clientSocket.dispose();
      } catch (final Throwable t) {
        logger.error("Unexpected error on closing outbound channel", t);
      }
    }
  }

  @Override
  public void write(final ByteBuffer buffer) {
    final Optional<RSocket> socket = prepareSocket();
    if (socket.isPresent()) {
      final RSocket rSocket = socket.get();
      //check if channel still open
      if (!rSocket.isDisposed()) {
        //Copy original buffer data because payload might not be sent immediately.
        ByteBuffer data = ByteBuffer.allocate(buffer.capacity());
        data.put(buffer);
        data.flip();

        final Payload payload = DefaultPayload.create(data);
        rSocket.fireAndForget(payload)
               .onErrorResume(throwable -> {
                 if (throwable instanceof ClosedChannelException) {
                   //close outbound channel
                   rSocket.dispose();
                   logger.error("Connection with {} closed", address, throwable);
                   return Mono.error(throwable);
                 } else {
                   logger.error("Failed write to {}, because: {}", address, throwable.getMessage(), throwable);
                   return Mono.empty();
                 }
               })
               .doFinally(signalType -> data.clear())
               .subscribe();
      } else {
        logger.warn("RSocket outbound channel for {} is closed. Message dropped", this.address);
      }
    } else {
      logger.debug("RSocket outbound channel for {} not ready. Message dropped", this.address);
    }
  }

  private Optional<RSocket> prepareSocket() {
    if (this.clientSocket == null) {
      try {
        this.clientSocket = RSocketFactory.connect()
                                          .frameDecoder(PayloadDecoder.ZERO_COPY)
                                          .transport(TcpClientTransport.create(address.hostName(), address.port()))
                                          .start()
                                          .retryBackoff(connectionRetries, connectionRetryBackoff)
                                          .block();
        logger.info("RSocket outbound channel opened for {}", this.address);

        this.clientSocket.onClose()
                         .doFinally(signalType -> logger.info("RSocket outbound channel for {} is closed", this.address))
                         .subscribe(ignored -> {}, throwable -> logger.error("Unexpected error on closing outbound channel", throwable));
      } catch (final Throwable e) {
        logger.error("Failed to create RSocket outbound channel for {}", this.address);
        return Optional.empty();
      }
    }

    return Optional.ofNullable(this.clientSocket);
  }
}
