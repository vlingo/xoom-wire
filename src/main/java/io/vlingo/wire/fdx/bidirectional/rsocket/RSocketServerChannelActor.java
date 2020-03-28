// Copyright © 2012-2020 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.fdx.bidirectional.rsocket;

import io.rsocket.AbstractRSocket;
import io.rsocket.Closeable;
import io.rsocket.ConnectionSetupPayload;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.RSocketFactory;
import io.rsocket.SocketAcceptor;
import io.rsocket.frame.decoder.PayloadDecoder;
import io.rsocket.transport.ServerTransport;
import io.vlingo.actors.Actor;
import io.vlingo.actors.ActorInstantiator;
import io.vlingo.actors.Logger;
import io.vlingo.actors.Stoppable;
import io.vlingo.common.Completes;
import io.vlingo.wire.channel.RequestChannelConsumerProvider;
import io.vlingo.wire.fdx.bidirectional.ServerRequestResponseChannel;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class RSocketServerChannelActor extends Actor implements ServerRequestResponseChannel {
  private final String name;
  private final Closeable serverSocket;
  private final Integer port;

  public RSocketServerChannelActor(final RequestChannelConsumerProvider provider, final ServerTransport<? extends Closeable> serverTransport, final int port,
                                   final String name, final int maxBufferPoolSize, final int messageBufferSize) {
    this.name = name;
    this.port = port;
    this.serverSocket = RSocketFactory.receive()
                                      .errorConsumer(throwable -> {
                                        logger().error("Unexpected error in server channel", throwable);
                                      })
                                      .frameDecoder(PayloadDecoder.ZERO_COPY)
                                      .acceptor(new SocketAcceptorImpl(provider, maxBufferPoolSize, messageBufferSize, logger()))
                                      .transport(serverTransport)
                                      .start()
                                      .block();

    if (this.serverSocket != null) {
      logger().info("RSocket server channel opened at port {}", this.port);

      this.serverSocket.onClose()
                       .doFinally(signalType -> logger().info("RSocket server channel closed"))
                       .subscribe();
    }
  }

  @Override
  public void close() {
    if (isStopped())
      return;

    if (this.serverSocket != null) {
      try {
        this.serverSocket.dispose();
      } catch (final Exception e) {
        logger().error("Failed to close receive socket for: {}", name, e);
      }
    }

    selfAs(Stoppable.class).stop();
  }

  @Override
  public Completes<Integer> port() {
    return completes().with(this.port);
  }

  @Override
  public void stop() {
    super.stop();
  }

  private static class SocketAcceptorImpl implements SocketAcceptor {
    private final RSocket acceptor;

    private SocketAcceptorImpl(final RequestChannelConsumerProvider consumerProvider, final int maxBufferPoolSize, final int maxMessageSize,
                               final Logger logger) {
      this.acceptor = new AbstractRSocket() {
        @Override
        public Flux<Payload> requestChannel(final Publisher<Payload> payloads) {
          final RSocketChannelContext context = new RSocketChannelContext(consumerProvider, maxBufferPoolSize, maxMessageSize, logger);

          Flux.from(payloads)
              .subscribeOn(Schedulers.single())
              .doOnNext(context::consume)
              .doOnError((throwable) -> logger.error("Unexpected error when consuming channel request", throwable))
              .subscribe();

          return Flux.from(context.processor());
        }
      };
    }

    @Override
    public Mono<RSocket> accept(ConnectionSetupPayload setupPayload, RSocket reactiveSocket) {
      return Mono.just(acceptor);
    }
  }

  public static class Instantiator implements ActorInstantiator<RSocketServerChannelActor> {
    private static final long serialVersionUID = -1999865617618138682L;

    private final RequestChannelConsumerProvider provider;
    private final ServerTransport<? extends Closeable> serverTransport;
    private final int port;
    private final String name;
    private final int maxBufferPoolSize;
    private final int messageBufferSize;

    public Instantiator(final RequestChannelConsumerProvider provider, final ServerTransport<? extends Closeable> serverTransport, final int port,
                        final String name, final int maxBufferPoolSize, final int messageBufferSize) {

      this.provider = provider;
      this.serverTransport = serverTransport;
      this.port = port;
      this.name = name;
      this.maxBufferPoolSize = maxBufferPoolSize;
      this.messageBufferSize = messageBufferSize;
    }

    @Override
    public RSocketServerChannelActor instantiate() {
      return new RSocketServerChannelActor(provider, serverTransport, port, name, maxBufferPoolSize, messageBufferSize);
    }

    @Override
    public Class<RSocketServerChannelActor> type() {
      return RSocketServerChannelActor.class;
    }
  }
}
