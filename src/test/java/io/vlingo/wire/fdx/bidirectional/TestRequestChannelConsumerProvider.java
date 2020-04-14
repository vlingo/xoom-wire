package io.vlingo.wire.fdx.bidirectional;

import io.vlingo.wire.channel.RequestChannelConsumer;
import io.vlingo.wire.channel.RequestChannelConsumerProvider;

import static io.vlingo.wire.fdx.bidirectional.TestRequestChannelConsumer.*;

public class TestRequestChannelConsumerProvider implements RequestChannelConsumerProvider {
  public State state;
  public RequestChannelConsumer consumer = new TestRequestChannelConsumer();

  @Override
  public RequestChannelConsumer requestChannelConsumer() {
    return consumer;
  }
}
