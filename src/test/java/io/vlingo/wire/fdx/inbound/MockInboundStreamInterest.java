// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.fdx.inbound;

import java.util.ArrayList;
import java.util.List;

import io.vlingo.actors.testkit.AccessSafely;
import io.vlingo.wire.message.AbstractMessageTool;
import io.vlingo.wire.message.RawMessage;
import io.vlingo.wire.node.AddressType;

public class MockInboundStreamInterest extends AbstractMessageTool implements InboundStreamInterest {
  public TestResults testResults;

  public MockInboundStreamInterest(int happenings) {
    testResults = new TestResults(happenings);
  }
  
  @Override
  public void handleInboundStreamMessage(final AddressType addressType, final RawMessage message) {
    final String textMessage = message.asTextMessage();
    testResults.addMessage(textMessage);
    System.out.printf("INTEREST: %s; count: %s; count-down: %s%n",
            textMessage, testResults.messageCount(), testResults.remaining());
  }

  static class TestResults {
    private static final String ID_MESSAGES = "messages";

    private final int happenings;
    private final AccessSafely accessSafely;

    TestResults(int happenings) {
      this.happenings = happenings;
      List<String> messages = new ArrayList<>();
      this.accessSafely = AccessSafely
              .afterCompleting(happenings)
              .writingWith(ID_MESSAGES, msg -> messages.add((String) msg))
              .readingWith(ID_MESSAGES, () -> new ArrayList<>(messages));
    }

    void addMessage(String msg) {
      accessSafely.writeUsing(ID_MESSAGES, msg);
    }

    List<String> getMessages() {
      return accessSafely.readFrom(ID_MESSAGES);
    }

    int messageCount() {
      return accessSafely.totalWrites();
    }

    int remaining() {
      return happenings - messageCount();
    }
  }
}
