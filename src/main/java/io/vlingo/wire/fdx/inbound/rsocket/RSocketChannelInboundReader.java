// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.
package io.vlingo.wire.fdx.inbound.rsocket;

import io.rsocket.AbstractRSocket;
import io.rsocket.ConnectionSetupPayload;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.RSocketFactory;
import io.rsocket.SocketAcceptor;
import io.rsocket.frame.decoder.PayloadDecoder;
import io.rsocket.transport.netty.server.TcpServerTransport;
import io.vlingo.actors.Logger;
import io.vlingo.wire.channel.ChannelMessageDispatcher;
import io.vlingo.wire.channel.ChannelReader;
import io.vlingo.wire.channel.ChannelReaderConsumer;
import io.vlingo.wire.message.RawMessageBuilder;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;

public class RSocketChannelInboundReader implements ChannelReader, ChannelMessageDispatcher {
  private final Logger logger;
  private final String name;
  private final int port;
  private boolean closed = false;
  private final int maxMessageSize;
  private Disposable receiveSocketDisposable;
  private ChannelReaderConsumer consumer;

  public RSocketChannelInboundReader(final int port, final String name, final int maxMessageSize, final Logger logger) {
    this.logger = logger;
    this.name = name;
    this.port = port;
    this.maxMessageSize = maxMessageSize;
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
    if (closed)
      return;

    closed = true;

    if (this.receiveSocketDisposable != null) {
      this.receiveSocketDisposable.dispose();
    }
  }

  @Override
  public String name() {
    return this.name;
  }

  @Override
  public void openFor(final ChannelReaderConsumer consumer) {
    if (closed)
      return; // for some tests it's possible to receive close() before start()
    this.logger.debug(getClass().getSimpleName() + ": OPENING PORT: {}", port);
    this.consumer = consumer;

    //Close existing receiving socket
    if (this.receiveSocketDisposable != null) {
      this.receiveSocketDisposable.dispose();
    }

    this.receiveSocketDisposable = RSocketFactory.receive()
                                                 .frameDecoder(PayloadDecoder.ZERO_COPY)
                                                 .acceptor(new SocketAcceptorImpl(this, maxMessageSize, logger))
                                                 .transport(TcpServerTransport.create(this.port))
                                                 .start()
                                                 .subscribe();
  }

  @Override
  public void probeChannel() {
    //Incoming messages are processed by receiveSocketDisposable.
  }

  private static class SocketAcceptorImpl implements SocketAcceptor {
    private final RSocket acceptor;

    private SocketAcceptorImpl(final ChannelMessageDispatcher dispatcher, final int maxMessageSize, final Logger logger) {
      final RawMessageBuilder rawMessageBuilder = new RawMessageBuilder(maxMessageSize);

      this.acceptor = new AbstractRSocket() {
        @Override
        public Mono<Void> fireAndForget(Payload payload) {
          try {
            final ByteBuffer payloadData = payload.getData();

            rawMessageBuilder.workBuffer().put(payloadData);

            dispatcher.dispatchMessagesFor(rawMessageBuilder);
          } catch (Exception e) {
            logger.error("Unexpected error", e);
          } finally {
            //Important because using PayloadDecoder.ZERO_COPY frame decoder
            payload.release();
          }
          return Mono.empty();
        }
      };
    }

    @Override
    public Mono<RSocket> accept(ConnectionSetupPayload setupPayload, RSocket reactiveSocket) {
      return Mono.just(acceptor);
    }
  }

}
