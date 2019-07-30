// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.fdx.bidirectional.rsocket;

import io.rsocket.AbstractRSocket;
import io.rsocket.ConnectionSetupPayload;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.RSocketFactory;
import io.rsocket.SocketAcceptor;
import io.rsocket.frame.decoder.PayloadDecoder;
import io.rsocket.transport.netty.server.CloseableChannel;
import io.rsocket.transport.netty.server.TcpServerTransport;
import io.vlingo.actors.Actor;
import io.vlingo.actors.Logger;
import io.vlingo.actors.Stoppable;
import io.vlingo.wire.channel.RequestChannelConsumerProvider;
import io.vlingo.wire.fdx.bidirectional.ServerRequestResponseChannel;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class RSocketServerChannelActor extends Actor implements ServerRequestResponseChannel {
  private final String name;
  private final CloseableChannel receiveSocketDisposable;

  public RSocketServerChannelActor(final RequestChannelConsumerProvider provider, final int port, final String name, final int maxBufferPoolSize,
          final int messageBufferSize) {
    this.name = name;
    this.receiveSocketDisposable = RSocketFactory.receive()
                                                 .frameDecoder(PayloadDecoder.ZERO_COPY)
                                                 .acceptor(new SocketAcceptorImpl(provider, maxBufferPoolSize, messageBufferSize, logger()))
                                                 .transport(TcpServerTransport.create(port))
                                                 .start()
                                                 .block();
    logger().info("RSocket server channel opened at port {}", port);
  }

  @Override
  public void close() {
    if (isStopped())
      return;

    if (this.receiveSocketDisposable != null) {
      try {
        this.receiveSocketDisposable.dispose();
      } catch (final Exception e) {
        logger().error("Failed to close receive socket for: {}", name, e);
      }
    }

    selfAs(Stoppable.class).stop();
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

}
