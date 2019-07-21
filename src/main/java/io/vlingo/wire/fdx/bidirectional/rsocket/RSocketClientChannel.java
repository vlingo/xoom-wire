// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.fdx.bidirectional.rsocket;

import io.rsocket.Payload;
import io.rsocket.RSocketFactory;
import io.rsocket.frame.decoder.PayloadDecoder;
import io.rsocket.transport.netty.client.TcpClientTransport;
import io.rsocket.util.ByteBufPayload;
import io.vlingo.actors.Logger;
import io.vlingo.wire.channel.ResponseChannelConsumer;
import io.vlingo.wire.fdx.bidirectional.ClientRequestResponseChannel;
import io.vlingo.wire.message.ByteBufferPool;
import io.vlingo.wire.message.ConsumerByteBuffer;
import io.vlingo.wire.node.Address;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.UnicastProcessor;

import java.nio.ByteBuffer;
import java.time.Duration;

public class RSocketClientChannel implements ClientRequestResponseChannel {
  private final Disposable connectSocket;
  private final UnicastProcessor<Payload> publisher;
  private final FluxSink<Payload> fluxSink;

  public RSocketClientChannel(final Address address, final ResponseChannelConsumer consumer, final int maxBufferPoolSize, final int maxMessageSize,
          final Logger logger) {
    this(address, consumer, maxBufferPoolSize, maxMessageSize, logger, 10, Duration.ofSeconds(1));
  }

  public RSocketClientChannel(final Address address, final ResponseChannelConsumer consumer, final int maxBufferPoolSize, final int maxMessageSize,
          final Logger logger, final int serverConnectRetries, final Duration serverConnectRetryBackoff) {

    this.publisher = UnicastProcessor.create();
    this.fluxSink = publisher.sink(FluxSink.OverflowStrategy.BUFFER);

    final ChannelResponseHandler responseHandler = new ChannelResponseHandler(consumer, maxBufferPoolSize, maxMessageSize, logger);
    this.connectSocket = RSocketFactory.connect()
                                       .frameDecoder(PayloadDecoder.ZERO_COPY)
                                       .keepAliveAckTimeout(Duration.ofMinutes(10))
                                       .transport(TcpClientTransport.create(address.hostName(), address.port()))
                                       .start()
                                       .retryBackoff(serverConnectRetries, serverConnectRetryBackoff)
                                       .subscribe(rSocket -> {
                                         rSocket.requestChannel(this.publisher)
                                                .doOnNext(payload -> responseHandler.handle(payload))
                                                .onErrorResume((throwable) -> {
                                                  logger.error("Failed request because: {}", throwable.getMessage(), throwable);
                                                  return Flux.empty();
                                                })
                                                .subscribe();
                                       }, throwable -> {
                                         logger.error("Unexpected client socket error", throwable);
                                       });
  }

  @Override
  public void close() {
    if (this.publisher != null) {
      this.publisher.cancel();
    }

    if (this.fluxSink != null && !this.fluxSink.isCancelled()) {
      this.fluxSink.complete();
    }

    if (this.connectSocket != null && !this.connectSocket.isDisposed()) {
      this.connectSocket.dispose();
    }
  }

  @Override
  public void requestWith(final ByteBuffer buffer) {
    this.fluxSink.next(ByteBufPayload.create(buffer));
  }

  @Override
  public void probeChannel() {
    //Incoming messages are processed by connectSocket
  }

  private static class ChannelResponseHandler {
    private final ResponseChannelConsumer consumer;
    private final Logger logger;
    private final ByteBufferPool readBufferPool;

    private ChannelResponseHandler(final ResponseChannelConsumer consumer, final int maxBufferPoolSize, final int maxMessageSize, final Logger logger) {
      this.consumer = consumer;
      this.readBufferPool = new ByteBufferPool(maxBufferPoolSize, maxMessageSize);
      this.logger = logger;
    }

    private void handle(Payload payload) {
      final ByteBufferPool.PooledByteBuffer pooledBuffer = readBufferPool.accessFor("client-response", 25);
      try {
        final ByteBuffer payloadData = payload.getData();
        final ConsumerByteBuffer put = pooledBuffer.put(payloadData);
        consumer.consume(put.flip());
      } catch (final Throwable e) {
        logger.error("Unexpected error reading incoming payload", e);
      } finally {
        payload.release();
        if (pooledBuffer.isInUse()) {
          pooledBuffer.release();
        }
      }
    }
  }

}
