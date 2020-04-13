// Copyright © 2012-2020 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.fdx.bidirectional;

import io.vlingo.actors.testkit.AccessSafely;
import io.vlingo.wire.channel.ResponseChannelConsumer;
import io.vlingo.wire.message.ConsumerByteBuffer;
import io.vlingo.wire.message.Converters;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class TestResponseChannelConsumer implements ResponseChannelConsumer {
  public int currentExpectedResponseLength;
  public int consumeCount;
  public List<String> responses = new ArrayList<>();
  public State state;

  private final StringBuilder responseBuilder = new StringBuilder();

  @Override
  public void consume(final ConsumerByteBuffer buffer) {
    final String responsePart = Converters.bytesToText(buffer.array(), 0, buffer.limit());
    responseBuilder.append(responsePart);
    if (responseBuilder.length() >= currentExpectedResponseLength) {
      // assume currentExpectedRequestLength is length of all
      // requests when multiple are received at one time
      final String combinedResponse = responseBuilder.toString();
      final int combinedLength = combinedResponse.length();

      int currentIndex = 0;
      boolean last = false;
      while (!last) {
        final String request = combinedResponse.substring(currentIndex, currentIndex + currentExpectedResponseLength);
        currentIndex += currentExpectedResponseLength;

        responses.add(request);
        state.access.writeUsing("consumeCount", 1);

        responseBuilder.setLength(0); // reuse
        if (currentIndex + currentExpectedResponseLength > combinedLength) {
          //Received combined responses has a part of a response.
          // Should save the part and append to the next combined responses.
          last = true;
          responseBuilder.append(combinedResponse, currentIndex, combinedLength);
        } else {
          last = currentIndex == combinedLength;
        }
      }
    } buffer.release();
  }

  public static class State {
    public AccessSafely access;
    AtomicInteger consumeCount = new AtomicInteger(0);
    AtomicInteger remaining;

    public State(final int totalWrites) {
      this.remaining = new AtomicInteger(totalWrites);
      this.access = afterCompleting(totalWrites);
    }

    private AccessSafely afterCompleting(final int totalWrites) {
      access = AccessSafely
              .afterCompleting(totalWrites)
              .writingWith("consumeCount", (Integer increment) -> increment())
              .readingWith("consumeCount", consumeCount::get)
              .readingWith("remaining", remaining::get);
      return access;
    }

    private void increment() {
      consumeCount.incrementAndGet();
      remaining.decrementAndGet();
    }
  }
}
