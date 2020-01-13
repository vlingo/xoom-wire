// Copyright © 2012-2020 VLINGO LABS. All rights reserved.
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
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.time.Duration;
import java.util.Optional;

public class RSocketOutboundChannel implements ManagedOutboundChannel {
  private final Address address;
  private final Logger logger;
  private RSocket clientSocket;
  private final Duration connectionTimeout;

  public RSocketOutboundChannel(final Address address, final Logger logger) {
    this(address, Duration.ofMillis(100), logger);
  }

  public RSocketOutboundChannel(final Address address, Duration connectionTimeout, final Logger logger) {
    this.address = address;
    this.logger = logger;
    this.connectionTimeout = connectionTimeout;
  }

  @Override
  public void close() {
    if (this.clientSocket != null && !this.clientSocket.isDisposed()) {
      try {
        this.clientSocket.dispose();
      } catch (final Exception t) {
        logger.error("Unexpected error on closing outbound channel", t);
      }
    }
    this.clientSocket = null;
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
                                          .timeout(connectionTimeout)
                                          .block();
        
        logger.info("RSocket outbound channel opened for {}", this.address);

        this.clientSocket.onClose()
                         .doFinally(signalType -> logger.info("RSocket outbound channel for {} is closed", this.address))
                         .subscribe(ignored -> {}, throwable -> logger.error("Unexpected error on closing outbound channel", throwable));
      } catch (final Exception t) {
        logger.warn("Failed to create RSocket outbound channel for {}, because {}", this.address, t.getMessage());
        close();
        return Optional.empty();
      }
    }

    return Optional.ofNullable(this.clientSocket);
  }
}
