// Copyright © 2012-2020 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.
package io.vlingo.wire.fdx.bidirectional.netty.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.EmptyByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.ReferenceCountUtil;
import io.vlingo.common.pool.ElasticResourcePool;
import io.vlingo.wire.channel.ResponseChannelConsumer;
import io.vlingo.wire.fdx.bidirectional.ClientRequestResponseChannel;
import io.vlingo.wire.message.ConsumerByteBuffer;
import io.vlingo.wire.message.ConsumerByteBufferPool;
import io.vlingo.wire.node.Address;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of {@link ClientRequestResponseChannel} based on Netty (https://netty.io/wiki/user-guide-for-4.x.html)
 */
public class NettyClientChannel implements ClientRequestResponseChannel {
  private final static Logger logger = LoggerFactory.getLogger(NettyClientChannel.class);
  private final EventLoopGroup workerGroup;
  private final Address address;
  private ChannelFuture channelFuture;

  public NettyClientChannel(final Address address, final ResponseChannelConsumer consumer, final int maxBufferPoolSize, final int maxMessageSize,
                            final Duration connectionTimeout) {
    this.address = address;
    this.workerGroup = new NioEventLoopGroup();

    try {
      this.channelFuture = new Bootstrap().group(this.workerGroup)
                                          .channel(NioSocketChannel.class)
                                          .option(ChannelOption.SO_KEEPALIVE, true)
                                          .handler(new NettyOutboundHandler(consumer, maxBufferPoolSize, maxMessageSize))
                                          .connect(address.hostName(), address.port())
                                          .sync();
      
      this.channelFuture.await(connectionTimeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      logger.error(e.getMessage(), e);
    }
  }

  @Override
  public void close() {
    try {
      if (this.channelFuture != null) {
        this.channelFuture.channel()
                          .close()
                          .await()
                          .sync();
      }

      if (this.workerGroup != null) {
        this.workerGroup.shutdownGracefully()
                        .await()
                        .sync();
      }

      logger.info("Netty client actor for {} closed", this.address);
    } catch (Throwable throwable) {
      logger.error("Netty client actor for {} was not closed properly", this.address, throwable);
    }
  }

  @Override
  public void requestWith(final ByteBuffer buffer) {
    if (this.channelFuture != null && !this.channelFuture.isCancelled()) {
      final ByteBuf request = this.channelFuture.channel()
                                                .alloc()
                                                .buffer(buffer.limit());
      request.writeBytes(buffer);

      this.channelFuture.channel()
                        .writeAndFlush(request)
                        .addListener(future -> {
                          request.release();
                          if (future.isSuccess()) {
                            logger.trace("Request sent");
                          }
                        });
    }
  }

  @Override
  public void probeChannel() {

  }

  private final static class NettyOutboundHandler extends ChannelInboundHandlerAdapter {
    private final ResponseChannelConsumer consumer;
    private final ConsumerByteBufferPool readBufferPool;

    private NettyOutboundHandler(final ResponseChannelConsumer consumer, final int maxBufferPoolSize, final int maxMessageSize) {
      this.consumer = consumer;
      this.readBufferPool = new ConsumerByteBufferPool(ElasticResourcePool.Config.of(maxBufferPoolSize), maxMessageSize);
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
      if (msg == null || msg == Unpooled.EMPTY_BUFFER || msg instanceof EmptyByteBuf) {
        return;
      }
      logger.trace("Response received");
      try {
        final ConsumerByteBuffer pooledBuffer = readBufferPool.acquire("NettyOutboundHandler#channelRead");
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
        ctx.close();
      } finally {
        ReferenceCountUtil.release(msg);
      }
    }
  }
}
