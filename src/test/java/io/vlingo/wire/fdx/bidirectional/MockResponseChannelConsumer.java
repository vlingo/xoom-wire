// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.fdx.bidirectional;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import io.vlingo.actors.testkit.TestUntil;
import io.vlingo.wire.channel.ResponseChannelConsumer;
import io.vlingo.wire.message.Converters;

public class MockResponseChannelConsumer implements ResponseChannelConsumer {
  public int currentExpectedResponseLength;
  public int consumeCount;
  public List<String> responses = new ArrayList<>();
  public TestUntil untilConsume;
  
  private final StringBuilder responseBuilder = new StringBuilder();
  
  @Override
  public void consume(final ByteBuffer buffer) {
    final String responsePart = Converters.bytesToText(buffer.array(), 0, buffer.limit());
    responseBuilder.append(responsePart);
    
    if (responseBuilder.length() >= currentExpectedResponseLength) {
      // assume currentExpectedRequestLength is length of all
      // requests when multiple are received at one time
      final String combinedResponse = responseBuilder.toString();
      final int combinedLength = combinedResponse.length();
      responseBuilder.setLength(0); // reuse
      
      int currentIndex = 0;
      boolean last = false;
      while (!last) {
        final String request = combinedResponse.substring(currentIndex, currentIndex+currentExpectedResponseLength);
        currentIndex += currentExpectedResponseLength;
        
        responses.add(request);
        ++consumeCount;
        
        last = currentIndex == combinedLength;
        
        untilConsume.happened();
      }
    }
  }
}
