// Copyright © 2012-2020 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.
package io.vlingo.wire.fdx.bidirectional.netty.server;

import java.util.concurrent.atomic.AtomicInteger;

import io.netty.channel.ChannelHandlerContext;
import io.vlingo.wire.channel.RequestResponseContext;
import io.vlingo.wire.channel.ResponseSenderChannel;
import io.vlingo.wire.message.ConsumerByteBuffer;

final class NettyServerChannelContext implements RequestResponseContext<ConsumerByteBuffer> {
  private static AtomicInteger contextId = new AtomicInteger(0);

  private final ChannelHandlerContext nettyChannelContext;
  @SuppressWarnings("unused")
  private Object closingData;
  private Object consumerData;
  private final String id;
  private ResponseSenderChannel sender;

//  private boolean explicitClose;

  NettyServerChannelContext(final ChannelHandlerContext nettyChannelContext, final ResponseSenderChannel sender) {
    this.nettyChannelContext = nettyChannelContext;
    this.sender = sender;

//    this.explicitClose = true;
    this.id = "" + contextId.incrementAndGet();
  }

  void eagerClose() {
//     if (explicitClose) return;
//     System.out.println(">>>>>>>>>>>>>>>>>>>>> NettyServerChannelContext::eagerClose()");
//     final ChannelHandlerContext channelHandlerContext = getNettyChannelContext();
//     if (channelHandlerContext.channel().isOpen()) {
//       channelHandlerContext.flush();
//       channelHandlerContext.close();
//     }
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T consumerData() {
    System.out.println("########### CHANNEL CONTEXT GET CONSUMER DATA (" + id + "): " + consumerData);
    return (T) consumerData;
  }

  @Override
  public <T> T consumerData(final T workingData) {
    System.out.println("########### CHANNEL CONTEXT SET CONSUMER DATA (" + id + "): " + workingData);
    this.consumerData = workingData;
    return workingData;
  }

  @Override
  public boolean hasConsumerData() {
    return consumerData != null;
  }

  @Override
  public String id() {
    return id;
  }

  void requireExplicitClose(final boolean option) {
//    explicitClose = option;
  }

  @Override
  public ResponseSenderChannel sender() {
    return this.sender;
  }

  @Override
  public void whenClosing(final Object data) {
    this.closingData = data;
  }

  ChannelHandlerContext getNettyChannelContext() {
    if (!nettyChannelContext.channel().isOpen()) {
      System.out.println(">>>>>>>>>>>>>>>>>>>>> AFTER CLOSE: NettyServerChannelContext::getNettyChannelContext()");
      throw new IllegalStateException("NettyServerChannelContext: Channel closed");
    }
    return nettyChannelContext;
  }
}
