// Copyright © 2012-2020 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.
package io.vlingo.wire.fdx.bidirectional.netty.client;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.EmptyByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
import io.vlingo.common.pool.ElasticResourcePool;
import io.vlingo.wire.channel.ResponseChannelConsumer;
import io.vlingo.wire.message.ConsumerByteBuffer;
import io.vlingo.wire.message.ConsumerByteBufferPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class NettyChannelResponseHandler extends ChannelInboundHandlerAdapter {
  private final static Logger logger = LoggerFactory.getLogger(NettyChannelResponseHandler.class);

  private final ResponseChannelConsumer consumer;
  private final ConsumerByteBufferPool readBufferPool;

  NettyChannelResponseHandler(final ResponseChannelConsumer consumer, final int maxBufferPoolSize, final int maxMessageSize) {
    this.consumer = consumer;
    this.readBufferPool = new ConsumerByteBufferPool(ElasticResourcePool.Config.of(maxBufferPoolSize), maxMessageSize);
  }

  @Override
  public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
    if (msg == null || msg == Unpooled.EMPTY_BUFFER || msg instanceof EmptyByteBuf) {
      return;
    }
    logger.trace("Response received");
    try {
      final ConsumerByteBuffer pooledBuffer = readBufferPool.acquire("NettyClientChannel#channelRead");
      try {
        final ByteBuf byteBuf = (ByteBuf) msg;
        byte[] bytes = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(bytes);
        pooledBuffer.put(bytes);

        consumer.consume(pooledBuffer.flip());
      } catch (Throwable t) {
        pooledBuffer.release();
        throw t;
      }
    } catch (Throwable throwable) {
      logger.error("Error reading the incoming data.", throwable);
    } finally {
      ReferenceCountUtil.release(msg);
    }
  }

  @Override
  public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) throws Exception {
    super.exceptionCaught(ctx, cause);
    logger.error("Unexpected exception", cause);
  }
}
