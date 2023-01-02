// Copyright © 2012-2023 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.xoom.wire.fdx.bidirectional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.vlingo.xoom.actors.testkit.AccessSafely;
import io.vlingo.xoom.wire.channel.RequestChannelConsumer;
import io.vlingo.xoom.wire.channel.RequestResponseContext;
import io.vlingo.xoom.wire.message.BasicConsumerByteBuffer;
import io.vlingo.xoom.wire.message.ConsumerByteBuffer;
import io.vlingo.xoom.wire.message.Converters;

public class TestRequestChannelConsumer implements RequestChannelConsumer {
  int currentExpectedRequestLength;
  public int consumeCount;
  List<String> requests = new ArrayList<>();
  State state;
  
  private StringBuilder requestBuilder = new StringBuilder();
  private String remaining = "";

  @Override
  public void closeWith(final RequestResponseContext<?> requestResponseContext, final Object data) {
  }

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
        state.access.writeUsing("consumeCount", 1);

        final ConsumerByteBuffer responseBuffer = new BasicConsumerByteBuffer(1, currentExpectedRequestLength);
        context.respondWith(responseBuffer.clear().put(request.getBytes()).flip()); // echo back
        
        last = currentIndex == combinedLength;
      }
    }
  }

  public static class State {
    AccessSafely access;
    final String[] answers;
    int index;
    final AtomicInteger consumeCount = new AtomicInteger(0);

    public State(final int totalWrites) {
      this.answers = new String[totalWrites];
      this.index = 0;
      this.access = afterCompleting(totalWrites);
    }

    private AccessSafely afterCompleting(final int totalWrites) {
      access = AccessSafely
              .afterCompleting(totalWrites)
              .writingWith("consumeCount", (Integer increment) -> consumeCount.set(consumeCount.incrementAndGet()))
              .readingWith("consumeCount", consumeCount::get);
      return access;
    }
  }
}
