// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.fdx.inbound;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import io.vlingo.actors.testkit.AccessSafely;
import io.vlingo.wire.message.AbstractMessageTool;
import io.vlingo.wire.message.RawMessage;
import io.vlingo.wire.node.AddressType;

public class MockInboundStreamInterest extends AbstractMessageTool implements InboundStreamInterest {
  public TestResults testResults = new TestResults();

  public MockInboundStreamInterest() { }
  
  @Override
  public void handleInboundStreamMessage(final AddressType addressType, final RawMessage message) {
    final String textMessage = message.asTextMessage();
    System.out.println("INTEREST: " + textMessage + " list-size: " + testResults.messages.size()
        + " count: " + testResults.messageCount.get()
        + " total-writes: " + testResults.handleInboundStreamMessageCalls.totalWrites());
    testResults.handleInboundStreamMessageCalls.writeUsing("handleInboundStreamMessage", addressType, message);
  }

  static class TestResults {
    public final AtomicInteger messageCount = new AtomicInteger(0);
    public final List<String> messages = new CopyOnWriteArrayList<>();

    private AccessSafely handleInboundStreamMessageCalls = AccessSafely.afterCompleting(0);

    /**
     * Answer with an AccessSafely which writes addressType, message to "handleInboundStreamMessage" and reads the write count from "completed".
     * <p>
     * Note: Clients can replace the default lambdas with their own via readingWith/writingWith.
     * 
     * @param n Number of times handleInboundStreamMessage(addressType, message) must be called before readFrom(...) will return.
     * @return
     */
    public AccessSafely expectHandleInboundStreamMessageTimes(final int n) {
      handleInboundStreamMessageCalls = AccessSafely.afterCompleting(n)
          .writingWith("handleInboundStreamMessage", (addressType, message) -> {
            final String textMessage = ((RawMessage) message).asTextMessage();
            messages.add(textMessage);
            messageCount.incrementAndGet();
          })
          .readingWith("completed", () -> handleInboundStreamMessageCalls.totalWrites())
          ;
      return handleInboundStreamMessageCalls;
    }

  }
}
