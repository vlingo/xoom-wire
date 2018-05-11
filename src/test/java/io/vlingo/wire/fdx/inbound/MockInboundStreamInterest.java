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

import io.vlingo.actors.testkit.TestUntil;
import io.vlingo.wire.message.AbstractMessageTool;
import io.vlingo.wire.message.RawMessage;
import io.vlingo.wire.node.AddressType;

public class MockInboundStreamInterest extends AbstractMessageTool implements InboundStreamInterest {
  public TestResults testResults = new TestResults();

  public MockInboundStreamInterest() { }
  
  @Override
  public void handleInboundStreamMessage(final AddressType addressType, final RawMessage message) {
    final String textMessage = message.asTextMessage();
    testResults.messages.add(textMessage);
    testResults.messageCount.incrementAndGet();
    System.out.println("INTEREST: " + textMessage + " list-size: " + testResults.messages.size() + " count: " + testResults.messageCount.get() + " count-down: " + testResults.untilStops.remaining());
    testResults.untilStops.happened();
  }

  static class TestResults {
    public final AtomicInteger messageCount = new AtomicInteger(0);
    public final List<String> messages = new CopyOnWriteArrayList<>();
    public TestUntil untilStops;
  }
}
