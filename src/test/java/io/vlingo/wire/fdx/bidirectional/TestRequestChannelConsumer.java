// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.fdx.bidirectional;

import java.util.ArrayList;
import java.util.List;

import io.vlingo.actors.testkit.TestUntil;
import io.vlingo.wire.channel.RequestChannelConsumer;
import io.vlingo.wire.channel.RequestResponseContext;
import io.vlingo.wire.message.BasicConsumerByteBuffer;
import io.vlingo.wire.message.ConsumerByteBuffer;
import io.vlingo.wire.message.Converters;

public class TestRequestChannelConsumer implements RequestChannelConsumer {
  public int currentExpectedRequestLength;
  public int consumeCount;
  public List<String> requests = new ArrayList<>();
  public TestUntil untilConsume;
  
  private StringBuilder requestBuilder = new StringBuilder();
  private String remaining = "";
  
  @Override
  public void consume(RequestResponseContext<?> context, final ConsumerByteBuffer buffer) {
    final String requestPart = Converters.bytesToText(buffer.array(), 0, buffer.limit());
    buffer.release();
    requestBuilder.append(remaining).append(requestPart);
    remaining = "";
    if (requestBuilder.length() >= currentExpectedRequestLength) {
      // assume currentExpectedRequestLength is length of all
      // requests when multiple are received at one time
      final String combinedRequests = requestBuilder.toString();
      final int combinedLength = combinedRequests.length();
      requestBuilder.setLength(0); // reuse
      
      int currentIndex = 0;
      boolean last = false;
      while (!last) {
        final int endIndex = currentIndex+currentExpectedRequestLength;
        if (endIndex > combinedRequests.length()) {
          remaining = combinedRequests.substring(currentIndex);
          return;
        }
        final String request = combinedRequests.substring(currentIndex, endIndex);
        currentIndex += currentExpectedRequestLength;
        requests.add(request);
        ++consumeCount;
        
        final ConsumerByteBuffer responseBuffer = new BasicConsumerByteBuffer(1, currentExpectedRequestLength);
        context.respondWith(responseBuffer.clear().put(request.getBytes()).flip()); // echo back
        
        last = currentIndex == combinedLength;
        
        untilConsume.happened();
      }
    }
  }
}
