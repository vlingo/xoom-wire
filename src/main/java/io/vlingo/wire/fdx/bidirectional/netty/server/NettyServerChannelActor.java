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
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.vlingo.actors.Actor;
import io.vlingo.actors.ActorInstantiator;
import io.vlingo.common.Completes;
import io.vlingo.wire.channel.RequestChannelConsumerProvider;
import io.vlingo.wire.fdx.bidirectional.ServerRequestResponseChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NettyServerChannelActor extends Actor implements ServerRequestResponseChannel {
  private final static Logger logger = LoggerFactory.getLogger(NettyServerChannelActor.class);

  private final int port;
  private final String name;
  private final EventLoopGroup bossGroup;
  private final EventLoopGroup workerGroup;
  private final ChannelFuture channelFuture;

  public NettyServerChannelActor(final RequestChannelConsumerProvider provider, final int port, final String name, final int processorPoolSize,
                                 final int maxBufferPoolSize, final int maxMessageSize) {
    this.port = port;
    this.name = name;

    this.bossGroup = new NioEventLoopGroup();
    this.workerGroup = new NioEventLoopGroup(processorPoolSize);

    try {
      final ServerBootstrap b = new ServerBootstrap();

      this.channelFuture = b.group(bossGroup, workerGroup)
                            .channel(NioServerSocketChannel.class)
                            .childHandler(new ChannelInitializer<SocketChannel>() {
                              @Override
                              public void initChannel(SocketChannel ch) throws Exception {
                                ch.pipeline()
                                  .addLast(new NettyClientHandler(provider, maxBufferPoolSize, maxMessageSize));
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
    try {
      this.channelFuture.channel()
                        .close()
                        .await()
                        .sync();

      this.bossGroup.shutdownGracefully()
                    .await()
                    .sync();

      this.workerGroup.shutdownGracefully()
                      .await()
                      .sync();

      logger.info("Netty server actor {} closed", this.name);
    } catch (Throwable throwable) {
      logger.error("Netty server actor {} was not closed properly", this.name, throwable);
    }
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

    public Instantiator(final RequestChannelConsumerProvider provider, final int port, final String name, final int processorPoolSize,
                        final int maxBufferPoolSize, final int maxMessageSize) {
      this.provider = provider;
      this.port = port;
      this.name = name;
      this.processorPoolSize = processorPoolSize;
      this.maxBufferPoolSize = maxBufferPoolSize;
      this.maxMessageSize = maxMessageSize;
    }

    @Override
    public NettyServerChannelActor instantiate() {
      return new NettyServerChannelActor(this.provider, this.port, this.name, this.processorPoolSize, this.maxBufferPoolSize, this.maxMessageSize);
    }

    @Override
    public Class<NettyServerChannelActor> type() {
      return NettyServerChannelActor.class;
    }
  }
}
