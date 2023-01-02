// Copyright © 2012-2023 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.
package io.vlingo.xoom.wire.fdx.outbound.rsocket;

import io.rsocket.RSocket;
import io.rsocket.core.RSocketConnector;
import io.rsocket.frame.decoder.PayloadDecoder;
import io.rsocket.transport.ClientTransport;
import io.rsocket.util.ByteBufPayload;
import io.vlingo.xoom.common.Completes;
import io.vlingo.xoom.common.Scheduler;
import io.vlingo.xoom.wire.fdx.outbound.ManagedOutboundChannel;
import io.vlingo.xoom.wire.node.Address;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

public class RSocketOutboundChannel implements ManagedOutboundChannel {

  private static final Logger logger = LoggerFactory.getLogger(RSocketOutboundChannel.class);

  private final Scheduler scheduler = new Scheduler();
  private final Address address;
  private final Duration connectionTimeout;
  private final ClientTransport transport;
  private RSocket clientSocket;

  public RSocketOutboundChannel(final Address address, final ClientTransport clientTransport, final io.vlingo.xoom.actors.Logger logger) {
    this(address, clientTransport, Duration.ofMillis(100), logger);
  }

  public RSocketOutboundChannel(final Address address, final ClientTransport clientTransport,
                                final Duration connectionTimeout, final io.vlingo.xoom.actors.Logger logger) {
    this.address = address;
    this.connectionTimeout = connectionTimeout;
    this.transport = clientTransport;
  }

  @Override
  public void close() {
    if (this.clientSocket != null && !this.clientSocket.isDisposed()) {
      try {
        this.clientSocket.dispose();
      } catch (final Throwable t) {
        logger.error("Unexpected error when closing outbound channel", t);
      }
    }
    this.clientSocket = null;
  }

  @Override
  public Completes<Void> writeAsync(final ByteBuffer buffer) {
    final Completes<Void> result = Completes.using(scheduler);
    writeAsyncInternal(buffer).subscribe(result::with, (t) -> result.failed());
    return result;
  }

  @Override
  public void write(final ByteBuffer buffer) {
    writeAsyncInternal(buffer).block();
  }

  private Mono<Void> writeAsyncInternal(final ByteBuffer buffer) {
    return prepareSocket()
            .map((socket) -> {
                if (socket.isDisposed()) {
                  logger.warn("RSocket outbound channel for {} is closed. Message dropped", this.address);
                  return Mono.<Void>empty();
                }
                return socket.fireAndForget(ByteBufPayload.create(buffer))
                             .doFinally(signalType -> {
                               logger.trace("Message sent to {}", this.address);
                             })
                             .doOnError(throwable -> {
                               logger.error("Failed write to {}, because: {}", address, throwable.getMessage(), throwable);
                             });
            })
            .orElseGet(() -> {
              logger.debug("RSocket outbound channel for {} not ready. Message dropped", address);
              return Mono.empty();
            });
  }

  private Optional<RSocket> prepareSocket() {
    if (this.clientSocket == null || this.clientSocket.isDisposed()) {
      try {
        this.clientSocket = RSocketConnector.create()
                                          .payloadDecoder(PayloadDecoder.ZERO_COPY)
                                          .connect(transport)
                                          .timeout(connectionTimeout, Mono.error(new TimeoutException("Timeout establishing connection for " + this.address)))
                                          .block();

        logger.info("RSocket outbound channel opened for {}", this.address);

        this.clientSocket.onClose()
                         .doFinally(signalType -> {
                           logger.info("RSocket outbound channel for {} is closed", this.address);
                           close();
                         })
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
