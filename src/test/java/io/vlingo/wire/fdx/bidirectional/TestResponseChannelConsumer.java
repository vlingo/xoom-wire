// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.fdx.bidirectional;

import java.util.ArrayList;
import java.util.List;

import io.vlingo.actors.testkit.AccessSafely;
import io.vlingo.wire.channel.ResponseChannelConsumer;
import io.vlingo.wire.message.ConsumerByteBuffer;
import io.vlingo.wire.message.Converters;

public class TestResponseChannelConsumer implements ResponseChannelConsumer {
  public int currentExpectedResponseLength;
  public int consumeCount;
  public List<String> responses = new ArrayList<>();

  private AccessSafely consumeCalls = AccessSafely.afterCompleting(0);
  private final StringBuilder responseBuilder = new StringBuilder();
  /**
   * Answer with an AccessSafely which writes buffer to "consume" and reads the write count from "completed".
   * <p>
   * Note: Clients can replace the default lambdas with their own via readingWith/writingWith.
   * 
   * @param n Number of times consume(buffer) must be called before readFrom(...) will return.
   * @return
   */
  public AccessSafely expectConsumeTimes(final int n) {
    consumeCalls = AccessSafely.afterCompleting(n)
        .writingWith("consume", buffer -> {})
        .readingWith("completed", () -> consumeCalls.totalWrites())
        ;
    return consumeCalls;
  }

  @Override
  public void consume(final ConsumerByteBuffer buffer) {
    final String responsePart = Converters.bytesToText(buffer.array(), 0, buffer.limit());
    buffer.release();
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
        
        consumeCalls.writeUsing("consume", buffer);
      }
    }
  }
}
