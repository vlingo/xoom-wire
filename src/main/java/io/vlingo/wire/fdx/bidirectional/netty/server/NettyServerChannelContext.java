// Copyright © 2012-2020 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.
package io.vlingo.wire.fdx.bidirectional.netty.server;

import io.netty.channel.ChannelHandlerContext;
import io.vlingo.wire.channel.RequestResponseContext;
import io.vlingo.wire.channel.ResponseSenderChannel;
import io.vlingo.wire.message.ConsumerByteBuffer;

final class NettyServerChannelContext implements RequestResponseContext<ConsumerByteBuffer> {

  private final ChannelHandlerContext nettyChannelContext;
  @SuppressWarnings("unused")
  private Object closingData;
  private Object consumerData;
  private ResponseSenderChannel sender;

  NettyServerChannelContext(final ChannelHandlerContext nettyChannelContext, final ResponseSenderChannel sender) {
    this.nettyChannelContext = nettyChannelContext;
    this.sender = sender;
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
    return null;
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
