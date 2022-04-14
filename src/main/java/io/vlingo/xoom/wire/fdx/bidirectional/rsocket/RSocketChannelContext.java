// Copyright © 2012-2022 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.
package io.vlingo.xoom.wire.fdx.bidirectional.rsocket;

import io.rsocket.Payload;
import io.rsocket.util.ByteBufPayload;
import io.vlingo.xoom.actors.Logger;
import io.vlingo.xoom.common.pool.ElasticResourcePool;
import io.vlingo.xoom.wire.channel.RequestChannelConsumer;
import io.vlingo.xoom.wire.channel.RequestChannelConsumerProvider;
import io.vlingo.xoom.wire.channel.RequestResponseContext;
import io.vlingo.xoom.wire.channel.ResponseSenderChannel;
import io.vlingo.xoom.wire.message.ConsumerByteBuffer;
import io.vlingo.xoom.wire.message.ConsumerByteBufferPool;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.UnicastProcessor;

@SuppressWarnings("deprecation")
class RSocketChannelContext implements RequestResponseContext<FluxSink<ConsumerByteBuffer>> {
  private final RequestChannelConsumer consumer;
  private final Logger logger;
  private final ConsumerByteBufferPool readBufferPool;
  private final UnicastProcessor<Payload> processor;
  private Object closingData;
  private Object consumerData;

  RSocketChannelContext(final RequestChannelConsumerProvider consumerProvider, final int maxBufferPoolSize, final int maxMessageSize, final Logger logger) {
    this.consumer = consumerProvider.requestChannelConsumer();
    this.logger = logger;
    this.readBufferPool = new ConsumerByteBufferPool(
        ElasticResourcePool.Config.of(maxBufferPoolSize), maxMessageSize);

    processor = UnicastProcessor.create();
  }

  UnicastProcessor<Payload> processor() {
    return processor;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T consumerData() {
    return ((T) consumerData);
  }

  @Override
  public <T> T consumerData(final T workingData) {
    this.consumerData = workingData;
    return workingData;
  }

  @Override
  public boolean hasConsumerData() {
    return this.consumerData != null;
  }

  @Override
  public String id() {
    return null;
  }

  @Override
  public ResponseSenderChannel sender() {
    return null;
  }

  @Override
  public void whenClosing(final Object data) {
    this.closingData = data;
  }

  public void close() {
    if (closingData != null) {
      try {
        this.consumer.closeWith(this, closingData);
      } catch (Exception e) {
        logger.error("Failed to close client channel because: " + e.getMessage(), e);
      }
    }
  }

  public void consume(Payload request) {
    final ConsumerByteBuffer pooledBuffer = readBufferPool.acquire("RSocketChannelContext#consume");
    try {
      pooledBuffer.put(request.getData());
      this.consumer.consume(this, pooledBuffer.flip());
    } catch(Throwable t) {
      pooledBuffer.release();
      throw t;
    }
  }

  @Override
  public void abandon() {
    close();
    processor.dispose();
  }

  @Override
  public void respondWith(final ConsumerByteBuffer buffer) {
    processor.onNext(ByteBufPayload.create(buffer.asByteBuffer()));
  }
}
