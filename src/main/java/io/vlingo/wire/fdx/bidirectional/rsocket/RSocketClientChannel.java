// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.fdx.bidirectional.rsocket;

import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.RSocketFactory;
import io.rsocket.exceptions.ApplicationErrorException;
import io.rsocket.frame.decoder.PayloadDecoder;
import io.rsocket.transport.netty.client.TcpClientTransport;
import io.rsocket.util.DefaultPayload;
import io.vlingo.actors.Logger;
import io.vlingo.wire.channel.ResponseChannelConsumer;
import io.vlingo.wire.fdx.bidirectional.ClientRequestResponseChannel;
import io.vlingo.wire.message.ByteBufferPool;
import io.vlingo.wire.message.ConsumerByteBuffer;
import io.vlingo.wire.node.Address;
import reactor.core.publisher.Flux;
import reactor.core.publisher.UnicastProcessor;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.ConcurrentLinkedQueue;

public class RSocketClientChannel implements ClientRequestResponseChannel {
  private final RSocket clientSocket;
  private final UnicastProcessor<Payload> publisher;

  public RSocketClientChannel(final Address address, final ResponseChannelConsumer consumer, final int maxBufferPoolSize, final int maxMessageSize,
                              final Logger logger) {
    this(address, consumer, maxBufferPoolSize, maxMessageSize, logger, 10, Duration.ofSeconds(1));
  }

  public RSocketClientChannel(final Address address, final ResponseChannelConsumer consumer, final int maxBufferPoolSize, final int maxMessageSize,
                              final Logger logger, final int serverConnectRetries, final Duration serverConnectRetryBackoff) {

    this.publisher = UnicastProcessor.create(new ConcurrentLinkedQueue<>());

    final ChannelResponseHandler responseHandler = new ChannelResponseHandler(consumer, maxBufferPoolSize, maxMessageSize, logger);
    this.clientSocket = RSocketFactory.connect()
                                      .frameDecoder(PayloadDecoder.ZERO_COPY)
                                      .keepAliveAckTimeout(Duration.ofMinutes(10))
                                      .transport(TcpClientTransport.create(address.hostName(), address.port()))
                                      .start()
                                      .retryBackoff(serverConnectRetries, serverConnectRetryBackoff)
                                      .block();

    if (this.clientSocket != null) {
      this.clientSocket.requestChannel(this.publisher)
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
                                    logger.error("Received an unrecoverable error. Channel will be closed", throwable);
                                    close();
                                  });
    }
  }

  @Override
  public void close() {
    if (this.publisher != null && !this.publisher.isDisposed()) {
      this.publisher.dispose();
    }

    if (this.clientSocket != null && !this.clientSocket.isDisposed()) {
      this.clientSocket.dispose();
    }
  }

  @Override
  public void requestWith(final ByteBuffer buffer) {
    if (!this.publisher.isTerminated()) {
      //Copy original buffer data because payload might not be sent immediately.
      ByteBuffer data = ByteBuffer.allocate(buffer.capacity());
      data.put(buffer);
      data.flip();

      this.publisher.onNext(DefaultPayload.create(data));
    } else {
      throw new IllegalStateException("Channel closed");
    }
  }

  @Override
  public void probeChannel() {
    //Incoming messages are processed by clientSocket
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
