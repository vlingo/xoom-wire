// Copyright © 2012-2020 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.
package io.vlingo.wire.fdx.bidirectional.netty.server;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.EmptyByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
import io.vlingo.common.pool.ElasticResourcePool;
import io.vlingo.wire.channel.RequestChannelConsumer;
import io.vlingo.wire.channel.RequestChannelConsumerProvider;
import io.vlingo.wire.channel.RequestResponseContext;
import io.vlingo.wire.channel.ResponseSenderChannel;
import io.vlingo.wire.message.ConsumerByteBuffer;
import io.vlingo.wire.message.ConsumerByteBufferPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class NettyClientHandler extends ChannelInboundHandlerAdapter implements ResponseSenderChannel {
  private final static Logger logger = LoggerFactory.getLogger(NettyClientHandler.class);
  private final RequestChannelConsumer consumer;
  private final ConsumerByteBufferPool readBufferPool;

  NettyClientHandler(final RequestChannelConsumerProvider consumerProvider, final int maxBufferPoolSize, final int maxMessageSize) {
    this.consumer = consumerProvider.requestChannelConsumer();
    this.readBufferPool = new ConsumerByteBufferPool(ElasticResourcePool.Config.of(maxBufferPoolSize), maxMessageSize);
  }

  @Override
  public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
    if (msg == null || msg == Unpooled.EMPTY_BUFFER || msg instanceof EmptyByteBuf) {
      return;
    }
    if (logger.isTraceEnabled()) {
      logger.debug("Request received");
    }
    try {
      final NettyServerChannelContext channelContext = new NettyServerChannelContext(ctx, this);

      final ConsumerByteBuffer pooledBuffer = readBufferPool.acquire("NettyClientHandler#channelRead");
      try {
        final ByteBuf byteBuf = (ByteBuf) msg;
        byte[] bytes = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(bytes);

        pooledBuffer.put(bytes);

        this.consumer.consume(channelContext, pooledBuffer.flip());
      } catch (Throwable t) {
        pooledBuffer.release();
        throw t;
      }
    } catch (Throwable throwable) {
      logger.error("Error reading the incoming data.", throwable);
      ctx.close();
    } finally {
      ReferenceCountUtil.release(msg);
    }
  }

  @Override
  public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) throws Exception {
    super.exceptionCaught(ctx, cause);
    logger.error("Unexpected exception", cause);
  }

  @Override
  public void abandon(final RequestResponseContext<?> context) {
    final ChannelHandlerContext nettyChannelContext = ((NettyServerChannelContext) context).getNettyChannelContext();
    nettyChannelContext.close();
  }

  @Override
  public void respondWith(final RequestResponseContext<?> context, final ConsumerByteBuffer buffer) {
    final ChannelHandlerContext nettyChannelContext = ((NettyServerChannelContext) context).getNettyChannelContext();

    final ByteBuf reply = nettyChannelContext.alloc()
                                             .buffer(buffer.limit());
    reply.writeBytes(buffer.asByteBuffer());

    nettyChannelContext.writeAndFlush(reply)
                       .addListener(future -> {
                         if (logger.isTraceEnabled()) {
                           logger.debug("Reply sent");
                         }
                       });
  }
}
