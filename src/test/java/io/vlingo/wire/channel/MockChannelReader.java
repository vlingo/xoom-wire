// Copyright © 2012-2020 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.channel;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import io.vlingo.wire.message.AbstractMessageTool;
import io.vlingo.wire.message.RawMessage;

public class MockChannelReader extends AbstractMessageTool implements ChannelReader {
  public static final String MessagePrefix = "Message-";
  
  public AtomicInteger probeChannelCount = new AtomicInteger(0);

  private ChannelReaderConsumer consumer;
  
  public MockChannelReader() {
  }
  
  @Override
  public void close() {

  }

  @Override
  public String name() {
    return "mock";
  }

  @Override
  public void openFor(final ChannelReaderConsumer consumer) throws IOException {
    this.consumer = consumer;
  }

  @Override
  public void probeChannel() {
    probeChannelCount.incrementAndGet();
    
    final RawMessage message = RawMessage.from(0, 0, MessagePrefix + probeChannelCount.get());
    
    consumer.consume(message);
  }
}
