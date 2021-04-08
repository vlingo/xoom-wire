// Copyright © 2012-2021 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.xoom.wire.fdx.inbound;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import io.vlingo.xoom.actors.testkit.AccessSafely;
import io.vlingo.xoom.wire.message.AbstractMessageTool;
import io.vlingo.xoom.wire.message.RawMessage;
import io.vlingo.xoom.wire.node.AddressType;

public class MockInboundStreamInterest extends AbstractMessageTool implements InboundStreamInterest {
  public TestResults testResults;

  public MockInboundStreamInterest() { }

  @Override
  public void handleInboundStreamMessage(final AddressType addressType, final RawMessage message) {
    final String textMessage = message.asTextMessage();
    testResults.access.writeUsing("messages", textMessage);
    testResults.access.writeUsing("messageCount", 1);
    System.out.println("INTEREST: " + textMessage +
            " list-size: " + testResults.access.readFrom("messagesSize") +
            " count: " + testResults.access.readFrom("messageCount") +
            " count-down: " + testResults.access.readFrom("remaining"));
  }

  public static class TestResults {
    AccessSafely access;
    final AtomicInteger messageCount = new AtomicInteger(0);
    final List<String> messages = new CopyOnWriteArrayList<>();
    AtomicInteger remaining;

    public TestResults(final int totalWrites) {
      this.remaining = new AtomicInteger(totalWrites);
      this.access = afterCompleting(totalWrites);
    }

    private AccessSafely afterCompleting(final int totalWrites) {
      access = AccessSafely
              .afterCompleting(totalWrites)
              .writingWith("messages", (Consumer<String>) messages::add)
              .readingWith("messages", (Integer index) -> messages.get(index))
              .readingWith("messagesSize", messages::size)
              .writingWith("messageCount", (Integer increment) -> increment())
              .readingWith("messageCount", messageCount::get)
              .readingWith("remaining", remaining::get);

      return access;
    }

    private void increment() {
      messageCount.incrementAndGet();
      remaining.decrementAndGet();
    }
  }
}
