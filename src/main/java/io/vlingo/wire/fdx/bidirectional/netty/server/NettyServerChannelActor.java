// Copyright © 2012-2020 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.
package io.vlingo.wire.fdx.bidirectional.netty.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.vlingo.actors.Actor;
import io.vlingo.actors.ActorInstantiator;
import io.vlingo.actors.Stoppable;
import io.vlingo.common.Completes;
import io.vlingo.wire.channel.RequestChannelConsumerProvider;
import io.vlingo.wire.fdx.bidirectional.ServerRequestResponseChannel;
import io.vlingo.wire.message.ConsumerByteBufferPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of {@link ServerRequestResponseChannel} based on Netty (https://netty.io/wiki/user-guide-for-4.x.html)
 */
public class NettyServerChannelActor extends Actor implements ServerRequestResponseChannel {
  private final static Logger logger = LoggerFactory.getLogger(NettyServerChannelActor.class);

  private enum OptimalTransport { NIO, Epoll };

  private final int port;
  private final String name;
  private final EventLoopGroup bossGroup;
  private final EventLoopGroup workerGroup;
  private final ChannelFuture channelFuture;
  private final Duration gracefulShutdownQuietPeriod;
  private final Duration gracefulShutdownTimeout;
  private final OptimalTransport optimalTransport;

  /**
   * Create a instance of {@link NettyServerChannelActor}.
   *
   * @param provider                    {@link RequestChannelConsumerProvider} provider
   * @param port                        server port to bind to
   * @param name                        server name, for logging
   * @param processorPoolSize           request processors pool size
   * @param maxBufferPoolSize           {@link ConsumerByteBufferPool} size
   * @param maxMessageSize              max message size
   * @param gracefulShutdownQuietPeriod graceful shutdown ensures that no tasks are submitted for <i>'the quiet period'</i> before it shuts itself down.
   * @param gracefulShutdownTimeout     the maximum amount of time to wait until the Netty resources are shut down
   */
  public NettyServerChannelActor(final RequestChannelConsumerProvider provider, final int port, final String name, final int processorPoolSize,
                                 final int maxBufferPoolSize, final int maxMessageSize, final Duration gracefulShutdownQuietPeriod,
                                 final Duration gracefulShutdownTimeout) {
    this.port = port;
    this.name = name;
    this.gracefulShutdownQuietPeriod = gracefulShutdownQuietPeriod;
    this.gracefulShutdownTimeout = gracefulShutdownTimeout;
    this.optimalTransport = optimalTransport();
    this.bossGroup = eventLoopGroup();
    this.workerGroup = eventLoopGroup(processorPoolSize);

    try {
      final ServerBootstrap b = new ServerBootstrap();
      final NettyInboundHandler inboundHandler = new NettyInboundHandler(provider, maxBufferPoolSize, maxMessageSize);

      this.channelFuture = b.group(bossGroup, workerGroup)
                            .channel(serverSocketChannelType())
                            .childHandler(new ChannelInitializer<SocketChannel>() {
                              @Override
                              public void initChannel(final SocketChannel channel) throws Exception {
                                channel
                                  .pipeline()
                                  .addLast(inboundHandler);
                              }
                            })
                            .bind(port)
                            .sync();
      logger.info("Netty server {} actor started", this.name);
    } catch (InterruptedException e) {
      logger.error("Netty server {} actor failed to initialize", this.name, e);
      throw new RuntimeException(e);
    }
  }

  @Override
  public void close() {
    if (isStopped())
      return;

    selfAs(Stoppable.class).stop();
  }

  @Override
  public void stop() {
    logger.debug("Netty server actor {} will stop", this.name);
    try {
      if (this.channelFuture.channel().isActive()) {
        this.channelFuture.channel()
                          .close()
                          .await()
                          .sync();
      }

      if (!this.bossGroup.isShutdown()) {
        this.bossGroup.shutdownGracefully(gracefulShutdownQuietPeriod.toMillis(), gracefulShutdownTimeout.toMillis(), TimeUnit.MILLISECONDS)
                      .await()
                      .sync();
      }

      if (!this.workerGroup.isShutdown()) {
        this.workerGroup.shutdownGracefully(gracefulShutdownQuietPeriod.toMillis(), gracefulShutdownTimeout.toMillis(), TimeUnit.MILLISECONDS)
                        .await()
                        .sync();
      }

      logger.info("Netty server actor {} closed", this.name);
    } catch (Throwable throwable) {
      logger.error("Netty server actor {} was not closed properly", this.name, throwable);
    }

    super.stop();
  }

  @Override
  public Completes<Integer> port() {
    return Completes.withSuccess(this.port);
  }

  public static class Instantiator implements ActorInstantiator<NettyServerChannelActor> {
    private static final long serialVersionUID = -5114262266054911219L;

    private final RequestChannelConsumerProvider provider;
    private final int port;
    private final String name;
    private final int processorPoolSize;
    private final int maxBufferPoolSize;
    private final int maxMessageSize;
    private final Duration gracefulShutdownQuietPeriod;
    private final Duration gracefulShutdownTimeout;

    /**
     * Create a instance of {@link NettyServerChannelActor}.
     *
     * @param provider          {@link RequestChannelConsumerProvider} provider
     * @param port              server port to bind to
     * @param name              server name, for logging
     * @param processorPoolSize request processors pool size
     * @param maxBufferPoolSize {@link ConsumerByteBufferPool} size
     * @param maxMessageSize    max message size
     */
    public Instantiator(final RequestChannelConsumerProvider provider, final int port, final String name, final int processorPoolSize,
                        final int maxBufferPoolSize, final int maxMessageSize) {
      this(provider, port, name, processorPoolSize, maxBufferPoolSize, maxMessageSize, Duration.ZERO, Duration.ZERO);
    }

    /**
     * Create a instance of {@link NettyServerChannelActor}, with support of graceful shutdown.
     *
     * @param provider                    {@link RequestChannelConsumerProvider} provider
     * @param port                        server port to bind to
     * @param name                        server name, for logging
     * @param processorPoolSize           request processors pool size
     * @param maxBufferPoolSize           {@link ConsumerByteBufferPool} size
     * @param maxMessageSize              max message size
     * @param gracefulShutdownQuietPeriod graceful shutdown ensures that no tasks are submitted for <b>gracefulShutdownQuietPeriod</b> before it shuts itself down.
     * @param gracefulShutdownTimeout     the maximum amount of time to wait until the Netty resources are shut down
     */
    public Instantiator(final RequestChannelConsumerProvider provider, final int port, final String name, final int processorPoolSize,
                        final int maxBufferPoolSize, final int maxMessageSize, final Duration gracefulShutdownQuietPeriod,
                        final Duration gracefulShutdownTimeout) {
      this.provider = provider;
      this.port = port;
      this.name = name;
      this.processorPoolSize = processorPoolSize;
      this.maxBufferPoolSize = maxBufferPoolSize;
      this.maxMessageSize = maxMessageSize;
      this.gracefulShutdownQuietPeriod = gracefulShutdownQuietPeriod;
      this.gracefulShutdownTimeout = gracefulShutdownTimeout;
    }

    @Override
    public NettyServerChannelActor instantiate() {
      return new NettyServerChannelActor(this.provider, this.port, this.name, this.processorPoolSize, this.maxBufferPoolSize, this.maxMessageSize,
                                         this.gracefulShutdownQuietPeriod, this.gracefulShutdownTimeout);
    }

    @Override
    public Class<NettyServerChannelActor> type() {
      return NettyServerChannelActor.class;
    }
  }

  private EventLoopGroup eventLoopGroup() {
    switch (optimalTransport) {
    case Epoll:
      logger.info("Netty server using EpollEventLoopGroup", this.name);
      return new EpollEventLoopGroup();
    case NIO:
    default:
      logger.info("Netty server using NioEventLoopGroup", this.name);
      return new NioEventLoopGroup();
    }
  }

  private EventLoopGroup eventLoopGroup(final int processorPoolSize) {
    switch (optimalTransport) {
    case Epoll:
      logger.info("Netty server using EpollEventLoopGroup " + processorPoolSize, this.name);
      return new EpollEventLoopGroup(processorPoolSize);
    case NIO:
    default:
      logger.info("Netty server using NioEventLoopGroup " + processorPoolSize, this.name);
      return new NioEventLoopGroup(processorPoolSize);
    }
  }

  private OptimalTransport optimalTransport() {
    final String osName = System.getProperty("os.name");

    logger.info("Netty server running on " + osName, this.name);

    if (osName.toLowerCase().contains("linux")) {
      return OptimalTransport.Epoll;
    }

    return OptimalTransport.NIO;
  }

  private Class<? extends ServerSocketChannel> serverSocketChannelType() {
    switch (optimalTransport) {
    case Epoll:
      logger.info("Netty server using EpollServerSocketChannel", this.name);
      return EpollServerSocketChannel.class;
    case NIO:
    default:
      logger.info("Netty server using NioServerSocketChannel", this.name);
      return NioServerSocketChannel.class;
    }
  }
}
