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
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.vlingo.wire.channel.ResponseChannelConsumer;
import io.vlingo.wire.fdx.bidirectional.ClientRequestResponseChannel;
import io.vlingo.wire.node.Address;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.time.Duration;

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
                                          .connect(address.hostName(), address.port())
                                          .sync();
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

  @Override
  public void probeChannel() {

  }
}
