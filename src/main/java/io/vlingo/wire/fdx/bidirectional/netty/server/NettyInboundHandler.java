// Copyright © 2012-2020 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.
package io.vlingo.wire.fdx.bidirectional.netty.server;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.EmptyByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
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
  private String contextInstanceId;
  private final Map<String, NettyServerChannelContext> contexts;
  private final ConsumerByteBufferPool readBufferPool;

  private static final AtomicLong nextInstanceId = new AtomicLong(0);
  private final long instanceId;

  NettyInboundHandler(final RequestChannelConsumerProvider consumerProvider, final int maxBufferPoolSize, final int maxMessageSize) {
    this.consumer = consumerProvider.requestChannelConsumer();
    this.readBufferPool = new ConsumerByteBufferPool(ElasticResourcePool.Config.of(maxBufferPoolSize), maxMessageSize);
    this.contexts = new HashMap<>();
    this.instanceId = nextInstanceId.incrementAndGet();
  }

  @Override
  public void channelRead(final ChannelHandlerContext context, final Object msg) {
    if (msg == null || msg == Unpooled.EMPTY_BUFFER || msg instanceof EmptyByteBuf) {
      return;
    }
    if (logger.isTraceEnabled()) {
      logger.debug("Request received");
    }

    logger.debug(">>>>> NettyInboundHandler::channelRead(): " + instanceId + " NAME: " + contextInstanceId(context));

    try {
      final String contextInstanceId = contextInstanceId(context);
      NettyServerChannelContext channelContext = contexts.get(contextInstanceId);
      if (channelContext == null) {
        channelContext = new NettyServerChannelContext(context, this);
        contexts.put(contextInstanceId, channelContext);
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
      context.close();
    } finally {
      ReferenceCountUtil.release(msg);
    }
  }

  @Override
  public void channelUnregistered(final ChannelHandlerContext context) throws Exception {
    logger.debug(">>>>> NettyInboundHandler::channelUnregistered(): " + instanceId + " NAME: " + contextInstanceId(context));
    contexts.remove(contextInstanceId(context));
    super.channelUnregistered(context);
  }

  @Override
  public void exceptionCaught(final ChannelHandlerContext context, final Throwable cause) throws Exception {
    logger.error("Unexpected exception", cause);
    super.exceptionCaught(context, cause);
  }

  @Override
  public void abandon(final RequestResponseContext<?> context) {
    final ChannelHandlerContext nettyChannelContext = ((NettyServerChannelContext) context).getNettyChannelContext();
    logger.debug(">>>>> NettyInboundHandler::abandon(): " + instanceId + " NAME: " + contextInstanceId(nettyChannelContext));
    nettyChannelContext.close();
  }

  @Override
  public void respondWith(final RequestResponseContext<?> context, final ConsumerByteBuffer buffer) {
    respondWith(context, buffer, false);
  }

  @Override
  public void respondWith(final RequestResponseContext<?> context, final ConsumerByteBuffer buffer, final boolean closeFollowing) {
    final NettyServerChannelContext nettyServerChannelContext = (NettyServerChannelContext) context;
    final ChannelHandlerContext channelHandlerContext = nettyServerChannelContext.getNettyChannelContext();

    logger.debug(">>>>> NettyInboundHandler::respondWith(): " + instanceId + " NAME: " + contextInstanceId(channelHandlerContext) + " : CLOSE? " + closeFollowing);

    final ByteBuf replyBuffer = channelHandlerContext.alloc().buffer(buffer.limit());

    replyBuffer.writeBytes(buffer.asByteBuffer());

    channelHandlerContext
      .writeAndFlush(replyBuffer)
      .addListener(new ChannelFutureListener() {
        @Override
        public void operationComplete(final ChannelFuture future) throws Exception {
          if (future.isSuccess()) {
            logger.debug("Reply sent");
          } else {
            logger.error("Failed to send reply", future.cause());
          }
          if (closeFollowing) {
            channelHandlerContext
              .close()
              .addListener(closeFuture -> {
                logger.debug(">>>>> NettyInboundHandler::respondWith(): " + instanceId + " NAME: " + contextInstanceId(channelHandlerContext) + " : CLOSED");
            });
          }
        }
      });
  }

  private String contextInstanceId(final ChannelHandlerContext context) {
    if (contextInstanceId == null) {
      contextInstanceId = context.name() + ":" + instanceId;
    }
    return contextInstanceId;
  }
}
