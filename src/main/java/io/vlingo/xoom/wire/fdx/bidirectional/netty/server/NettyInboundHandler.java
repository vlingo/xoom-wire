// Copyright © 2012-2020 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.
package io.vlingo.xoom.wire.fdx.bidirectional.netty.server;

import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.EmptyByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import io.vlingo.xoom.common.pool.ElasticResourcePool;
import io.vlingo.xoom.wire.channel.RequestChannelConsumer;
import io.vlingo.xoom.wire.channel.RequestChannelConsumerProvider;
import io.vlingo.xoom.wire.channel.RequestResponseContext;
import io.vlingo.xoom.wire.channel.ResponseSenderChannel;
import io.vlingo.xoom.wire.message.BasicConsumerByteBuffer;
import io.vlingo.xoom.wire.message.ConsumerByteBuffer;
import io.vlingo.xoom.wire.message.ConsumerByteBufferPool;

@ChannelHandler.Sharable
final class NettyInboundHandler extends ChannelInboundHandlerAdapter implements ResponseSenderChannel {
  private static final String CONNECTION_RESET = "Connection reset by peer";

  private final static Logger logger = LoggerFactory.getLogger(NettyInboundHandler.class);

  private static final String WIRE_CONTEXT_NAME = "$WIRE_CONTEXT";
  private static final AttributeKey<NettyServerChannelContext> WIRE_CONTEXT;

  static {
    WIRE_CONTEXT = AttributeKey.exists(WIRE_CONTEXT_NAME) ?
            AttributeKey.valueOf(WIRE_CONTEXT_NAME) :
            AttributeKey.newInstance(WIRE_CONTEXT_NAME);
  }

  private final RequestChannelConsumer consumer;
  private String contextInstanceId;
  private final ConsumerByteBufferPool readBufferPool;

  private static final AtomicLong nextInstanceId = new AtomicLong(0);
  private final long instanceId;

  NettyInboundHandler(final RequestChannelConsumerProvider consumerProvider, final int maxBufferPoolSize, final int maxMessageSize) {
    this.consumer = consumerProvider.requestChannelConsumer();
    this.readBufferPool = new ConsumerByteBufferPool(ElasticResourcePool.Config.of(maxBufferPoolSize), maxMessageSize);
    this.instanceId = nextInstanceId.incrementAndGet();
  }

  @Override
  public void channelActive(final ChannelHandlerContext ctx) throws Exception {
    logger.debug(">>>>> NettyInboundHandler::channelActive(): " + instanceId + " NAME: " + contextInstanceId(ctx));
    if (ctx.channel().isActive()) {
      getWireContext(ctx);
    }
  }

  @Override
  public void channelRead(final ChannelHandlerContext context, final Object msg) {
    if (msg == null || msg == Unpooled.EMPTY_BUFFER || msg instanceof EmptyByteBuf) {
      return;
    }
    logger.debug(">>>>> NettyInboundHandler::channelRead(): " + instanceId + " NAME: " + contextInstanceId(context));

    try {
      NettyServerChannelContext channelContext = getWireContext(context);

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
    super.channelUnregistered(context);
  }

  @Override
  public void exceptionCaught(final ChannelHandlerContext context, final Throwable cause) throws Exception {
    if (cause.getClass() == java.io.IOException.class && cause.getMessage().contains(CONNECTION_RESET)) {
      logger.error(CONNECTION_RESET);
    } else {
      logger.error("Unexpected exception", cause);
    }
    // super.exceptionCaught(context, cause);
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

    final String contextInstanceId = contextInstanceId(channelHandlerContext);
    logger.debug(">>>>> NettyInboundHandler::respondWith(): " + instanceId + " NAME: " + contextInstanceId + " : CLOSE? " + closeFollowing);

    final ByteBuf replyBuffer = channelHandlerContext.alloc().buffer(buffer.limit());

    replyBuffer.writeBytes(buffer.asByteBuffer());

    channelHandlerContext
      .writeAndFlush(replyBuffer)
      .addListener((ChannelFutureListener) future -> {
        if (future.isSuccess()) {
          logger.debug("Reply sent");
        } else {
          logger.error("Failed to send reply", future.cause());
        }
        if (closeFollowing) {
          closeConnection(contextInstanceId, future);
        }
      });
  }

  @Override
  public void respondWith(final RequestResponseContext<?> context, final Object response, final boolean closeFollowing) {
    final String textResponse = response.toString();

    final ConsumerByteBuffer buffer =
            new BasicConsumerByteBuffer(0, textResponse.length() + 1024)
            .put(textResponse.getBytes()).flip();

    respondWith(context, buffer, closeFollowing);
  }

  private void closeConnection(final String contextInstanceId, final ChannelFuture channelFuture) {
    channelFuture
      .channel()
      .close()
      .addListener(closeFuture -> {
        if (closeFuture.isSuccess()){
          logger.debug(">>>>> NettyInboundHandler::respondWith(): " + instanceId + " NAME: " + contextInstanceId + " : CLOSED");
        } else {
          logger.error(">>>>> NettyInboundHandler::respondWith(): " + instanceId + " NAME: " + contextInstanceId + " : FAILED TO CLOSE");
        }
    });
  }

  private NettyServerChannelContext getWireContext(final ChannelHandlerContext ctx) {
    final Channel nettyChannel = ctx.channel();
    if (!nettyChannel.hasAttr(WIRE_CONTEXT)){
      nettyChannel.attr(WIRE_CONTEXT)
         .set(new NettyServerChannelContext(ctx, this));
    }

    return nettyChannel.attr(WIRE_CONTEXT).get();
  }

  private String contextInstanceId(final ChannelHandlerContext context) {
    if (contextInstanceId == null) {
      contextInstanceId = context.name() + ":" + instanceId;
    }
    return contextInstanceId;
  }
}
