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
  private static final String ID_RESPONSES = "responses";

  private final int happenings;
  private final int currentExpectedResponseLength;
  private final AccessSafely accessSafely;

  private final StringBuilder responseBuilder = new StringBuilder();

  public TestResponseChannelConsumer(int happenings, int currentExpectedResponseLength) {
    this.happenings = happenings;
    this.currentExpectedResponseLength = currentExpectedResponseLength;
    List<String> responses = new ArrayList<>();
    this.accessSafely = AccessSafely.afterCompleting(happenings)
      .writingWith(ID_RESPONSES, msg -> responses.add((String) msg))
      .readingWith(ID_RESPONSES, () -> new ArrayList<>(responses));
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

        accessSafely.writeUsing(ID_RESPONSES, request);

        last = currentIndex == combinedLength;
      }
    }
  }

  public int remaining() {
    return happenings - accessSafely.totalWrites();
  }

  public int consumeCount() {
    return accessSafely.totalWrites();
  }

  public List<String> responses() {
    return accessSafely.readFrom(ID_RESPONSES);
  }
}
