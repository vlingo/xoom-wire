package io.vlingo.wire.fdx.bidirectional;

import io.vlingo.wire.channel.RequestChannelConsumer;
import io.vlingo.wire.channel.RequestChannelConsumerProvider;

public class TestRequestChannelConsumerProvider implements RequestChannelConsumerProvider {
  public RequestChannelConsumer consumer = new TestRequestChannelConsumer();

  @Override
  public RequestChannelConsumer requestChannelConsumer() {
    return consumer;
  }
}
