package io.vlingo.xoom.wire.fdx.bidirectional;

import io.vlingo.xoom.wire.channel.RequestChannelConsumer;
import io.vlingo.xoom.wire.channel.RequestChannelConsumerProvider;

import static io.vlingo.xoom.wire.fdx.bidirectional.TestRequestChannelConsumer.*;

public class TestRequestChannelConsumerProvider implements RequestChannelConsumerProvider {
  public State state;
  public RequestChannelConsumer consumer = new TestRequestChannelConsumer();

  @Override
  public RequestChannelConsumer requestChannelConsumer() {
    return consumer;
  }
}
