// Copyright © 2012-2021 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.
package io.vlingo.xoom.wire.fdx.bidirectional.netty.server;

import io.netty.channel.ChannelHandlerContext;
import io.vlingo.xoom.wire.channel.RequestResponseContext;
import io.vlingo.xoom.wire.channel.ResponseSenderChannel;
import io.vlingo.xoom.wire.message.ConsumerByteBuffer;

import java.util.concurrent.atomic.AtomicLong;

final class NettyServerChannelContext implements RequestResponseContext<ConsumerByteBuffer> {
  private static final AtomicLong contextId = new AtomicLong(0);

  private final ChannelHandlerContext nettyChannelContext;
  @SuppressWarnings("unused")
  private Object closingData;
  private Object consumerData;
  private final String id;
  private final ResponseSenderChannel sender;

  NettyServerChannelContext(final ChannelHandlerContext nettyChannelContext, final ResponseSenderChannel sender) {
    this.nettyChannelContext = nettyChannelContext;
    this.sender = sender;
    this.id = "" + contextId.incrementAndGet();
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T consumerData() {
    return (T) consumerData;
  }

  @Override
  public <T> T consumerData(final T workingData) {
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

  @Override
  public ResponseSenderChannel sender() {
    return this.sender;
  }

  @Override
  public void whenClosing(final Object data) {
    this.closingData = data;
  }

  ChannelHandlerContext getNettyChannelContext() {
    return nettyChannelContext;
  }
}
