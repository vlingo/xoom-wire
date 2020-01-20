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
import io.rsocket.transport.ClientTransport;
import io.rsocket.util.DefaultPayload;
import io.vlingo.actors.Logger;
import io.vlingo.common.Completes;
import io.vlingo.common.Scheduler;
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
  private final ClientTransport transport;

  public RSocketOutboundChannel(final Address address, final ClientTransport clientTransport, final Logger logger) {
    this(address, clientTransport, Duration.ofMillis(100), logger);
  }

  public RSocketOutboundChannel(final Address address, final ClientTransport clientTransport, Duration connectionTimeout, final Logger logger) {
    this.address = address;
    this.logger = logger;
    this.connectionTimeout = connectionTimeout;
    this.transport = clientTransport;
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
    this.clientSocket = null;
  }

  private final Scheduler scheduler = new Scheduler();

  @Override
  public Completes<Void> writeAsync(final ByteBuffer buffer) {
    Completes<Void> result = Completes.using(scheduler);
    _writeAsync(buffer).subscribe(result::with, (t) -> result.failed());
    return result;
  }

  private Mono<Void> _writeAsync(final ByteBuffer buffer) {
    return prepareSocket().map((socket) -> {
      if (socket.isDisposed()) {
        return Mono.fromRunnable(() ->
            logger.warn("RSocket outbound channel for {} is closed. Message dropped",
                this.address)).then();
      }
      final Payload payload = DefaultPayload.create(buffer);
      return socket.fireAndForget(payload)
          .onErrorResume(throwable -> {
            if (throwable instanceof ClosedChannelException) {
              //close outbound channel
              socket.dispose();
              logger.error("Connection with {} closed", address, throwable);
              return Mono.error(throwable);
            } else {
              logger.error("Failed write to {}, because: {}", address, throwable.getMessage(), throwable);
              return Mono.empty();
            }
          });
    }).orElseGet(() -> Mono.fromRunnable(() ->
        logger.debug("RSocket outbound channel for {} not ready. Message dropped",
            address)));
  }

  @Override
  public void write(final ByteBuffer buffer) {
    _writeAsync(buffer).block();
  }

  private Optional<RSocket> prepareSocket() {
    if (this.clientSocket == null) {
      try {
        this.clientSocket = RSocketFactory.connect()
                                          .errorConsumer(throwable -> {
                                            logger.error("Unexpected error in outbound channel", throwable);
                                          })
                                          .frameDecoder(PayloadDecoder.ZERO_COPY)
                                          .transport(transport)
                                          .start()
                                          .timeout(connectionTimeout)
                                          .block();

        logger.info("RSocket outbound channel opened for {}", this.address);

        this.clientSocket.onClose()
                         .doFinally(signalType -> logger.info("RSocket outbound channel for {} is closed", this.address))
                         .subscribe(ignored -> {}, throwable -> logger.error("Unexpected error on closing outbound channel", throwable));

      } catch (final Throwable t) {
        logger.warn("Failed to create RSocket outbound channel for {}, because {}", this.address, t.getMessage());
        close();
        return Optional.empty();
      }
    }

    return Optional.ofNullable(this.clientSocket);
  }
}
