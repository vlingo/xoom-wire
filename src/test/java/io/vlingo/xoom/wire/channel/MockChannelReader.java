// Copyright © 2012-2023 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.xoom.wire.channel;

import io.vlingo.xoom.actors.testkit.AccessSafely;
import io.vlingo.xoom.wire.message.AbstractMessageTool;
import io.vlingo.xoom.wire.message.RawMessage;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

public class MockChannelReader extends AbstractMessageTool implements ChannelReader {
  public static final String MessagePrefix = "Message-";

  public Results results;

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
  public int port() {
    return 0;
  }

  @Override
  public void openFor(final ChannelReaderConsumer consumer) throws IOException {
    this.consumer = consumer;
  }

  @Override
  public void probeChannel() {
    this.results.access.writeUsing("probeChannelCount", 1);
    
    final RawMessage message = RawMessage.from(0, 0, MessagePrefix + this.results.access.readFrom("probeChannelCount"));
    
    consumer.consume(message);
  }

  public static class Results {
    public AccessSafely access;
    public AtomicInteger probeChannelCount = new AtomicInteger(0);

    public Results(final int totalWrites) {
      this.access = afterCompleting(totalWrites);
    }

    private AccessSafely afterCompleting(final int totalWrites) {
      access = AccessSafely
              .afterCompleting(totalWrites)
              .writingWith("probeChannelCount", (Integer inc) -> probeChannelCount.incrementAndGet())
              .readingWith("probeChannelCount", probeChannelCount::get);

      return access;
    }
  }
}
