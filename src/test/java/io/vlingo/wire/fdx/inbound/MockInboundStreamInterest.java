// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.fdx.inbound;

import java.util.ArrayList;
import java.util.List;

import io.vlingo.wire.message.AbstractMessageTool;
import io.vlingo.wire.message.RawMessage;
import io.vlingo.wire.node.AddressType;

public class MockInboundStreamInterest extends AbstractMessageTool implements InboundStreamInterest {
  public int messageCount;
  public final List<String> messages = new ArrayList<>();

  public MockInboundStreamInterest() { }
  
  @Override
  public void handleInboundStreamMessage(final AddressType addressType, final RawMessage message) {
    ++messageCount;
    final String textMessage = message.asTextMessage();
    messages.add(textMessage);
  }
}
