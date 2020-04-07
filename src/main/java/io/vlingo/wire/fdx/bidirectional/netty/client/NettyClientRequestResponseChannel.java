// Copyright © 2012-2020 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.
package io.vlingo.wire.fdx.bidirectional.netty.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.vlingo.wire.channel.ResponseChannelConsumer;
import io.vlingo.wire.fdx.bidirectional.ClientRequestResponseChannel;
import io.vlingo.wire.message.ConsumerByteBuffer;
import io.vlingo.wire.message.ConsumerByteBufferPool;
import io.vlingo.wire.node.Address;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of {@link ClientRequestResponseChannel} based on <a href="https://netty.io/wiki/user-guide-for-4.x.html">Netty</a>
 * <p>
 * <b>Important</b>. Due to streaming nature of TCP, there is no guarantee that what is read is exactly what server wrote.
 * {@link ResponseChannelConsumer} might receive a {@link ConsumerByteBuffer} that contain a partial or event multiple server replies.
 * Because of that, and the fact that the channel has no knowledge of the nature of messages being exchanged, <b>the {@link ResponseChannelConsumer} is responsible for extracting the message</b>.
 * See Netty docs <a href="https://netty.io/wiki/user-guide-for-4.x.html#dealing-with-a-stream-based-transport">Dealing with a Stream-based Transport</a> for more information.
 * </p>
 * <p>
 * This channel hoverer guarantees that the {@link ConsumerByteBuffer} will never hold more bytes than what is configure by {@code maxMessageSize}.
 * A received {@link ByteBuf}, with size greater than {@code maxMessageSize}, will be split into multiple {@link ByteBuf}.
 * </p>
 */
public class NettyClientRequestResponseChannel implements ClientRequestResponseChannel {
  private final static Logger logger = LoggerFactory.getLogger(NettyClientRequestResponseChannel.class);

  private final Address address;
  private final ResponseChannelConsumer consumer;
  private final int maxBufferPoolSize;
  private final int maxMessageSize;
  private final Duration connectionTimeout;
  private ChannelFuture channelFuture;
  private EventLoopGroup workerGroup;
  private final Duration gracefulShutdownQuietPeriod;
  private final Duration gracefulShutdownTimeout;

  /**
   * Build a instance of client channel with support of graceful shutdown.
   *
   * @param address                     the address to connect to to
   * @param consumer                    {@link ResponseChannelConsumer} for consuming the response buffers
   * @param maxBufferPoolSize           {@link ConsumerByteBufferPool} size
   * @param maxMessageSize              max message size
   * @param connectionTimeout           connection timeout
   * @param gracefulShutdownQuietPeriod graceful shutdown ensures that no tasks are submitted for <i>'the quiet period'</i> before it shuts itself down.
   * @param gracefulShutdownTimeout     the maximum amount of time to wait until the Netty resources are shut down
   */
  public NettyClientRequestResponseChannel(final Address address, final ResponseChannelConsumer consumer, final int maxBufferPoolSize, final int maxMessageSize,
                                           final Duration connectionTimeout, final Duration gracefulShutdownQuietPeriod,
                                           final Duration gracefulShutdownTimeout) {
    this.address = address;
    this.consumer = consumer;
    this.maxBufferPoolSize = maxBufferPoolSize;
    this.maxMessageSize = maxMessageSize;
    this.connectionTimeout = connectionTimeout;
    this.gracefulShutdownQuietPeriod = gracefulShutdownQuietPeriod;
    this.gracefulShutdownTimeout = gracefulShutdownTimeout;
  }

  /**
   * Build a instance of client channel with default connection timeout of 1000ms and without graceful shutdown.
   *
   * @param address           the address to connect to to
   * @param consumer          {@link ResponseChannelConsumer} for consuming the response buffers
   * @param maxBufferPoolSize {@link ConsumerByteBufferPool} size
   * @param maxMessageSize    max message size
   */
  public NettyClientRequestResponseChannel(final Address address, final ResponseChannelConsumer consumer, final int maxBufferPoolSize,
                                           final int maxMessageSize) {
    this(address, consumer, maxBufferPoolSize, maxMessageSize, Duration.ofMillis(1000), Duration.ZERO, Duration.ZERO);
  }

  @Override
  public void close() {
    try {
      if (this.channelFuture != null && this.channelFuture.channel()
                                                          .isActive()) {
        this.channelFuture.channel()
                          .close()
                          .await()
                          .sync();
      }

      if (this.workerGroup != null && !this.workerGroup.isShutdown()) {
        this.workerGroup.shutdownGracefully(this.gracefulShutdownQuietPeriod.toMillis(), this.gracefulShutdownTimeout.toMillis(), TimeUnit.MILLISECONDS)
                        .await()
                        .sync();
      }
      logger.info("Netty client actor for {} closed", this.address);
    } catch (Throwable throwable) {
      logger.error("Netty client actor for {} was not closed properly", this.address, throwable);
    }
  }

  /**
   * Request to send a message.
   * <p>
   * Note that if the channel is not ready, the message will be dropped.
   */
  @Override
  public void requestWith(final ByteBuffer buffer) {
    prepareChannel().ifPresent(channelFuture -> {
      final ByteBuf requestByteBuff = channelFuture.channel()
                                                   .alloc()
                                                   .buffer(buffer.limit());
      requestByteBuff.writeBytes(buffer);

      channelFuture.channel()
                   .writeAndFlush(requestByteBuff)  //ByteBuf instance will be released by Netty
                   .addListener(future -> {
                     if (future.isSuccess()) {
                       logger.trace("Request sent");
                     } else {
                       logger.error("Failed to send request", future.cause());
                       //Close the channel in case of an error.
                       // Next request, or probeChannel() method invocation, will re-establish the connection
                       close();
                     }
                   });
    });
  }

  @Override
  public void probeChannel() {
    prepareChannel();
  }

  private Optional<ChannelFuture> prepareChannel() {
    if (this.workerGroup == null || this.workerGroup.isShutdown()) {
      this.workerGroup = new NioEventLoopGroup();
    }

    if (this.channelFuture == null || this.channelFuture.isCancelled()) {
      try {
        this.channelFuture = new Bootstrap().group(this.workerGroup)
                                            .channel(NioSocketChannel.class)
                                            .option(ChannelOption.SO_KEEPALIVE, true)
                                            .handler(new ChannelInitializer<NioSocketChannel>() {
                                              @Override
                                              protected void initChannel(final NioSocketChannel ch) {
                                                ch.pipeline()
                                                  .addLast(
                                                          //If io.netty log level is configured as TRACE, will output the inbound/outbound data
                                                          new LoggingHandler(LogLevel.TRACE),
                                                          // Split incoming data in chunks of max size = maxMessageSize
                                                          new MaxMessageSizeSplitter(maxMessageSize),
                                                          new NettyChannelResponseHandler(consumer, maxBufferPoolSize, maxMessageSize));
                                              }
                                            })
                                            .connect(this.address.hostName(), this.address.port())
                                            .sync();

        this.channelFuture.await(this.connectionTimeout.toMillis(), TimeUnit.MILLISECONDS);
      } catch (final InterruptedException e) {
        logger.error("Thread Interruption on client channel creation", e);
        Thread.currentThread()
              .interrupt();
        return Optional.empty();
      }
    }

    return Optional.ofNullable(this.channelFuture);
  }

  private final static class MaxMessageSizeSplitter extends ByteToMessageDecoder {
    private final int maxMessageSize;

    private MaxMessageSizeSplitter(final int maxMessageSize) {this.maxMessageSize = maxMessageSize;}

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
      if (in.readableBytes() < maxMessageSize) {
        out.add(in.readBytes(in.readableBytes()));
      } else {
        while (in.readableBytes() > 0) {
          if (in.readableBytes() < maxMessageSize) {
            out.add(in.readBytes(in.readableBytes()));
          } else {
            out.add(in.readBytes(maxMessageSize));
          }
        }
      }
    }
  }
}
