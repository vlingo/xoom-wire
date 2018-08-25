package io.vlingo.wire.fdx.bidirectional;

import io.vlingo.actors.testkit.TestUntil;
import io.vlingo.wire.channel.RequestChannelConsumer;
import io.vlingo.wire.channel.RequestChannelConsumerProvider;

public class TestRequestChannelConsumerProvider implements RequestChannelConsumerProvider {
  public TestUntil until;
  public RequestChannelConsumer consumer = new TestRequestChannelConsumer();

  @Override
  public RequestChannelConsumer requestChannelConsumer() {
    return consumer;
  }
}
