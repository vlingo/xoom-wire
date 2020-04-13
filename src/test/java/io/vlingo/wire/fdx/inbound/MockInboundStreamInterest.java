// Copyright © 2012-2020 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.fdx.inbound;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import io.vlingo.actors.testkit.TestUntil;
import io.vlingo.wire.message.AbstractMessageTool;
import io.vlingo.wire.message.RawMessage;
import io.vlingo.wire.node.AddressType;

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

  static class TestResults {
    public final AtomicInteger messageCount = new AtomicInteger(0);
    public final List<String> messages = new CopyOnWriteArrayList<>();
    public TestUntil untilStops;
  }
}
