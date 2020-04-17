// Copyright © 2012-2020 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.
package io.vlingo.wire.fdx.bidirectional.netty.server;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.EmptyByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
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

final class NettyInboundHandler extends ChannelInboundHandlerAdapter implements ResponseSenderChannel {
  private final static Logger logger = LoggerFactory.getLogger(NettyInboundHandler.class);
  private final RequestChannelConsumer consumer;
  private final ConsumerByteBufferPool readBufferPool;

  private static final AtomicInteger nextInstanceId = new AtomicInteger();
  private final int instanceId;

  NettyInboundHandler(final RequestChannelConsumerProvider consumerProvider, final int maxBufferPoolSize, final int maxMessageSize) {
    this.consumer = consumerProvider.requestChannelConsumer();
    this.readBufferPool = new ConsumerByteBufferPool(ElasticResourcePool.Config.of(maxBufferPoolSize), maxMessageSize);

    this.instanceId = nextInstanceId.incrementAndGet();
    System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>> NettyInboundHandler::ctor(): " + instanceId + " CONSUMER: " + consumer);
  }

  private final Map<String, NettyServerChannelContext> contexts = new HashMap<>();
  @Override
  public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
    if (msg == null || msg == Unpooled.EMPTY_BUFFER || msg instanceof EmptyByteBuf) {
      return;
    }
    if (logger.isTraceEnabled()) {
      logger.debug("Request received");
    }

    System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>> NettyInboundHandler::channelRead(): " + instanceId);

    try {
      NettyServerChannelContext channelContext = contexts.get(ctx.name());
      if (channelContext == null) {
        channelContext = new NettyServerChannelContext(ctx, this);
        contexts.put(ctx.name(), channelContext);
      }

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
    logger.trace(">>>>>>>>>>>>>>>>>>>>>>>>>>>> NettyInboundHandler::abandon(): " + instanceId);
    final ChannelHandlerContext nettyChannelContext = ((NettyServerChannelContext) context).getNettyChannelContext();
    nettyChannelContext.close();
  }

  @Override
  public void explicitClose(final RequestResponseContext<?> context, final boolean option) {

  }

  @Override
  public void respondWith(final RequestResponseContext<?> context, final ConsumerByteBuffer buffer) {
    respondWith(context, buffer, false);
  }

  @Override
  public void respondWith(final RequestResponseContext<?> context, final ConsumerByteBuffer buffer, final boolean closeFollowing) {
    System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>> NettyInboundHandler::respondWith(): " + instanceId + " : CLOSE? " + closeFollowing);

    final NettyServerChannelContext nettyServerChannelContext = (NettyServerChannelContext) context;
    final ChannelHandlerContext nettyChannelContext = nettyServerChannelContext.getNettyChannelContext();

    final ByteBuf replyBuffer = nettyChannelContext.alloc()
                                                   .buffer(buffer.limit());
    replyBuffer.writeBytes(buffer.asByteBuffer());

    try {
      final ChannelFuture writeCompletable = nettyChannelContext.writeAndFlush(replyBuffer);

      writeCompletable
        .addListener(future -> {
                           if (future.isSuccess()) {
                             logger.trace("Reply sent");
                           } else {
                             logger.error("Failed to send reply", future.cause());
                           }
                         });

      writeCompletable.await().syncUninterruptibly();
      if (writeCompletable.isDone() && closeFollowing) {
        nettyChannelContext.close();
      }

    } catch (InterruptedException e) {
      logger.trace("Timeout: NettyInboundHandler::respondWith() writeAndFlush().await()");
    }
  }
}
