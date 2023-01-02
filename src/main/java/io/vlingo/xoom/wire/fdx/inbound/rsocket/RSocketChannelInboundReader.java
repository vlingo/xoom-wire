// Copyright © 2012-2023 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.
package io.vlingo.xoom.wire.fdx.inbound.rsocket;

import io.rsocket.Closeable;
import io.rsocket.ConnectionSetupPayload;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.SocketAcceptor;
import io.rsocket.core.RSocketServer;
import io.rsocket.frame.decoder.PayloadDecoder;
import io.rsocket.transport.ServerTransport;
import io.vlingo.xoom.actors.Logger;
import io.vlingo.xoom.wire.channel.ChannelMessageDispatcher;
import io.vlingo.xoom.wire.channel.ChannelReader;
import io.vlingo.xoom.wire.channel.ChannelReaderConsumer;
import io.vlingo.xoom.wire.message.RawMessageBuilder;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;

public class RSocketChannelInboundReader implements ChannelReader, ChannelMessageDispatcher {
  private final Logger logger;
  private final String name;
  private final int port;
  private final int maxMessageSize;
  private Closeable serverSocket;
  private ChannelReaderConsumer consumer;
  private final ServerTransport<? extends Closeable> serverTransport;

  public RSocketChannelInboundReader(final ServerTransport<? extends Closeable> serverTransport,
                                     final int port, final String name, final int maxMessageSize,
                                     final Logger logger) {
    this.logger = logger;
    this.name = name;
    this.port = port;
    this.maxMessageSize = maxMessageSize;
    this.serverTransport = serverTransport;
  }

  @Override
  public ChannelReaderConsumer consumer() {
    return consumer;
  }

  @Override
  public Logger logger() {
    return this.logger;
  }

  @Override
  public void close() {
    if (this.serverSocket != null && !this.serverSocket.isDisposed()) {
      try {
        this.serverSocket.dispose();
      } catch (final Throwable t) {
        logger.error("Unexpected error on closing inbound channel {}", this.name, t);
      }
    }
  }

  @Override
  public String name() {
    return this.name;
  }

  @Override
  public int port() {
    return this.port;
  }

  @Override
  public void openFor(final ChannelReaderConsumer consumer) {
    this.consumer = consumer;

    //Close existing receiving socket
    if (this.serverSocket != null && !this.serverSocket.isDisposed()) {
      this.serverSocket.dispose();
    }

    serverSocket = RSocketServer.create()
                                 .payloadDecoder(PayloadDecoder.ZERO_COPY)
                                 .acceptor(new SocketAcceptorImpl(this, name, maxMessageSize, logger))
                                 .bind(serverTransport)
                                 .doOnError(throwable -> logger.error("Failed to create RSocket inbound channel {} at port {}", name, port, throwable))
                                 .block();

    if (serverSocket != null) {
      this.serverSocket.onClose()
                       .doFinally(signalType -> logger.info("RSocket inbound channel {} at port {} is closed", name, port))
                       .subscribe(ignored -> {}, throwable -> logger.error("Unexpected error on closing inbound channel {}", name, throwable));
      logger().info("RSocket inbound channel {} opened at port {}", name, port);
    }
  }

  @Override
  public void probeChannel() {
    //Incoming messages are processed by serverSocket.
  }

  private static class SocketAcceptorImpl implements SocketAcceptor {
    private final ChannelMessageDispatcher dispatcher;
    private final String name;
    private final int maxMessageSize;
    private final Logger logger;

    private SocketAcceptorImpl(final ChannelMessageDispatcher dispatcher, final String name, final int maxMessageSize, final Logger logger) {
      this.dispatcher = dispatcher;
      this.name = name;
      this.maxMessageSize = maxMessageSize;
      this.logger = logger;
    }

    @Override
    public Mono<RSocket> accept(ConnectionSetupPayload setupPayload, RSocket reactiveSocket) {
      final RawMessageBuilder rawMessageBuilder = new RawMessageBuilder(maxMessageSize);
      return Mono.just(buildAcceptor(rawMessageBuilder));
    }

    private RSocket buildAcceptor(RawMessageBuilder rawMessageBuilder) {
      return new RSocket() {
        @Override
        public Mono<Void> fireAndForget(Payload payload) {
          logger.trace("Message received on inbound channel {}", name);
          try {
            final ByteBuffer payloadData = payload.getData();
            rawMessageBuilder.workBuffer().put(payloadData);

            dispatcher.dispatchMessagesFor(rawMessageBuilder);
          } catch (final Throwable t) {
            logger.error("Unexpected error in inbound channel {}. Message ignored.", name, t);
          } finally {
            //Important! Because using PayloadDecoder.ZERO_COPY frame decoder
            payload.release();
            rawMessageBuilder.workBuffer().clear();
          }
          return Mono.empty();
        }
      };
    }
  }

}
