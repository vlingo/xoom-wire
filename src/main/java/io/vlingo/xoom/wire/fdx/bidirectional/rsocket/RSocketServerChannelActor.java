// Copyright © 2012-2022 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.xoom.wire.fdx.bidirectional.rsocket;

import io.rsocket.Closeable;
import io.rsocket.SocketAcceptor;
import io.rsocket.core.RSocketServer;
import io.rsocket.frame.decoder.PayloadDecoder;
import io.rsocket.transport.ServerTransport;
import io.vlingo.xoom.actors.Actor;
import io.vlingo.xoom.actors.ActorInstantiator;
import io.vlingo.xoom.actors.Stoppable;
import io.vlingo.xoom.common.Completes;
import io.vlingo.xoom.wire.channel.RequestChannelConsumerProvider;
import io.vlingo.xoom.wire.fdx.bidirectional.ServerRequestResponseChannel;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

public class RSocketServerChannelActor extends Actor implements ServerRequestResponseChannel {
  private final String name;
  private final Closeable serverSocket;
  private final Integer port;

  public RSocketServerChannelActor(final RequestChannelConsumerProvider provider, final ServerTransport<? extends Closeable> serverTransport, final int port,
                                   final String name, final int maxBufferPoolSize, final int messageBufferSize) {
    this.name = name;
    this.port = port;

    this.serverSocket = RSocketServer.create()
                                      .payloadDecoder(PayloadDecoder.ZERO_COPY)
                                      .acceptor(SocketAcceptor.forRequestChannel(payloads -> {
                                        final RSocketChannelContext context = new RSocketChannelContext(provider, maxBufferPoolSize, messageBufferSize, logger());

                                        Flux.from(payloads)
                                                .subscribeOn(Schedulers.single())
                                                .doOnNext(context::consume)
                                                .doOnError((throwable) -> logger().error("Unexpected error when consuming channel request", throwable))
                                                .subscribe();

                                        return Flux.from(context.processor());
                                      }))
                                      .bind(serverTransport)
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
    selfAs(Stoppable.class).stop();
  }

  @Override
  public Completes<Integer> port() {
    return completes().with(this.port);
  }

  @Override
  public void stop() {
    if (this.serverSocket != null) {
      try {
        this.serverSocket.dispose();
      } catch (final Exception e) {
        logger().error("Failed to close receive socket for: {}", name, e);
      }
    }

    super.stop();
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
