// Copyright © 2012-2020 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.fdx.bidirectional.rsocket;

import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.time.Duration;
import java.util.concurrent.ConcurrentLinkedQueue;

import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.RSocketFactory;
import io.rsocket.exceptions.ApplicationErrorException;
import io.rsocket.frame.decoder.PayloadDecoder;
import io.rsocket.transport.ClientTransport;
import io.rsocket.util.DefaultPayload;
import io.vlingo.actors.Logger;
import io.vlingo.common.pool.ElasticResourcePool;
import io.vlingo.wire.channel.ResponseChannelConsumer;
import io.vlingo.wire.fdx.bidirectional.ClientRequestResponseChannel;
import io.vlingo.wire.message.ConsumerByteBuffer;
import io.vlingo.wire.message.ConsumerByteBufferPool;
import io.vlingo.wire.node.Address;
import reactor.core.Disposable;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.UnicastProcessor;

public class RSocketClientChannel implements ClientRequestResponseChannel {
  private final UnicastProcessor<Payload> publisher;
  private final Logger logger;
  private final ChannelResponseHandler responseHandler;
  private final Address address;
  private final Duration connectionTimeout;
  private final ClientTransport transport;
  private RSocket channelSocket;

  public RSocketClientChannel(final ClientTransport clientTransport, final Address address, final ResponseChannelConsumer consumer,
                              final int maxBufferPoolSize, final int maxMessageSize, final Logger logger) {
    this(clientTransport, address, consumer, maxBufferPoolSize, maxMessageSize, logger, Duration.ofMillis(100));
  }

  public RSocketClientChannel(final ClientTransport clientTransport, final Address address, final ResponseChannelConsumer consumer,
                              final int maxBufferPoolSize, final int maxMessageSize,
                              final Logger logger, final Duration connectionTimeout) {
    this.publisher = UnicastProcessor.create(new ConcurrentLinkedQueue<>());
    this.logger = logger;
    this.address = address;
    this.connectionTimeout = connectionTimeout;
    this.responseHandler = new ChannelResponseHandler(consumer, maxBufferPoolSize, maxMessageSize, logger);
    this.transport = clientTransport;
  }

  @Override
  public void close() {
    if (this.channelSocket != null && !this.channelSocket.isDisposed()) {
      try {
        this.channelSocket.dispose();
      } catch (final Throwable t) {
        logger.error("Unexpected error on closing channel socket", t);
      }
    }
    this.channelSocket = null;
  }

  @Override
  public void requestWith(final ByteBuffer buffer) {
    prepareChannel();
    if (this.channelSocket != null && !this.channelSocket.isDisposed()) {
      //Copy original buffer data because payload might not be sent immediately.
      ByteBuffer data = ByteBuffer.allocate(buffer.capacity());
      data.put(buffer);
      data.flip();

      this.publisher.onNext(DefaultPayload.create(data));
    } else {
      logger.debug("RSocket client channel for {} not ready. Message dropped", this.address);
    }
  }

  @Override
  public void probeChannel() {
    prepareChannel();
    //Incoming messages are processed by channelSocket
  }

  private void prepareChannel() {
    try {
      if (this.channelSocket == null) {
        this.channelSocket = RSocketFactory.connect()
                                           .errorConsumer(throwable -> {
                                             logger.error("Unexpected error in channel", throwable);
                                           })
                                           .frameDecoder(PayloadDecoder.ZERO_COPY)
                                           .transport(transport)
                                           .start()
                                           .timeout(this.connectionTimeout)
                                           .doOnError(throwable -> {
                                             logger.error("Failed to create channel socket for address {}", this.address, throwable);
                                           })
                                           .block();

        if (this.channelSocket != null) {
          final Disposable subscribeFlow = this.channelSocket.requestChannel(this.publisher)
                                                             .onErrorResume((throwable) -> {
                                                               if (throwable instanceof ApplicationErrorException) {
                                                                 //We can resume processing on an application error
                                                                 logger.error("Server replied with an error: {}", throwable.getMessage(), throwable);
                                                                 return Flux.empty();
                                                               } else {
                                                                 //Can not be resumed, propagate.
                                                                 return Flux.error(throwable);
                                                               }
                                                             })
                                                             .subscribe(responseHandler::handle, //process server response
                                                                        throwable -> {    //process any errors that are unrecoverable
                                                                          this.publisher.cancel();
                                                                          if (!(throwable instanceof ClosedChannelException)) {
                                                                            this.logger.error("Received an unrecoverable error. Channel will be closed", throwable);
                                                                            //Propagate the error in order to close the channel
                                                                            throw Exceptions.propagate(throwable);
                                                                          }
                                                                        },
                                                                        () -> this.logger.info("Channel completed"));

          logger.info("RSocket client channel opened for address {}", this.address);

          this.channelSocket.onClose()
                            .doFinally(signalType -> {
                              if (!subscribeFlow.isDisposed()) {
                                subscribeFlow.dispose();
                              }
                              logger.info("RSocket client channel for address {} is closed", this.address);
                            })
                            .subscribe(ignored -> {}, throwable -> logger.error("Unexpected error on closing channel socket", throwable));
        }
      }
    } catch (final Throwable t) {
      logger.warn("Failed to create RSocket client channel for {}, because {}", this.address, t.getMessage());
      close();
    }
  }

  private static class ChannelResponseHandler {
    private final ResponseChannelConsumer consumer;
    private final Logger logger;
    private final ConsumerByteBufferPool readBufferPool;

    private ChannelResponseHandler(final ResponseChannelConsumer consumer, final int maxBufferPoolSize, final int maxMessageSize, final Logger logger) {
      this.consumer = consumer;
      this.readBufferPool = new ConsumerByteBufferPool(ElasticResourcePool.Config.of(maxBufferPoolSize), maxMessageSize);
      this.logger = logger;
    }

    private void handle(Payload payload) {
      final ConsumerByteBuffer pooledBuffer = readBufferPool.acquire("RSocketClientChannel#ChannelResponseHandler#handle");
      try {
        final ByteBuffer payloadData = payload.getData();
        consumer.consume(
            pooledBuffer.put(payloadData).flip());
      } catch (final Throwable e) {
        logger.error("Unexpected error reading incoming payload", e);
        pooledBuffer.release();
      } finally {
        payload.release();
      }
    }
  }

}
