// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.fdx.inbound;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import io.vlingo.wire.fdx.inbound.InboundResponder;
import io.vlingo.wire.fdx.inbound.InboundStreamInterest;
import io.vlingo.wire.message.AbstractMessageTool;
import io.vlingo.wire.message.RawMessage;
import io.vlingo.wire.node.AddressType;

public class MockInboundStreamInterest extends AbstractMessageTool implements InboundStreamInterest {
  private final ByteBuffer buffer;
  public int messageCount;
  public final List<String> messages = new ArrayList<>();

  public MockInboundStreamInterest() {
    this.buffer = ByteBuffer.allocate(1024);
  }
  
  @Override
  public void handleInboundStreamMessage(final AddressType addressType, final RawMessage message, final InboundResponder responder) {
    ++messageCount;
    final String textMessage = message.asTextMessage();
    messages.add(textMessage);
    
    try {
      final RawMessage responseMessage = buildRawMessageBuffer(buffer, "RESPOND-FOR: " + textMessage);
      
      responder.respondWith(responseMessage.asByteBuffer());
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}