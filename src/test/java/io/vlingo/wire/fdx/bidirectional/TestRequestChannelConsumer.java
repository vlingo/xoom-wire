// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.fdx.bidirectional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.vlingo.actors.testkit.AccessSafely;
import io.vlingo.wire.channel.RequestChannelConsumer;
import io.vlingo.wire.channel.RequestResponseContext;
import io.vlingo.wire.message.BasicConsumerByteBuffer;
import io.vlingo.wire.message.ConsumerByteBuffer;
import io.vlingo.wire.message.Converters;

public class TestRequestChannelConsumer implements RequestChannelConsumer {
  private static final String ID_REQUESTS = "requests";


  private final AtomicBoolean untilClosed;
  private final AccessSafely accessSafely;
  private final int happenings;
  private final int currentExpectedRequestLength;

  private StringBuilder requestBuilder = new StringBuilder();
  private String remaining = "";

  public TestRequestChannelConsumer(int happenings, int currentExpectedRequestLength) {
    this.happenings = happenings;
    this.currentExpectedRequestLength = currentExpectedRequestLength;
    List<String> requests = new ArrayList<>();
    this.untilClosed = new AtomicBoolean(false);
    this.accessSafely = AccessSafely.afterCompleting(happenings)
      .writingWith(ID_REQUESTS, msg -> requests.add((String) msg))
      .readingWith(ID_REQUESTS, () -> new ArrayList<>(requests));
  }

  @Override
  public void closeWith(final RequestResponseContext<?> requestResponseContext, final Object data) {
    untilClosed.set(true);
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
        final int endIndex = currentIndex + currentExpectedRequestLength;
        if (endIndex > combinedRequests.length()) {
          remaining = combinedRequests.substring(currentIndex);
          return;
        }
        final String request = combinedRequests.substring(currentIndex, endIndex);
        currentIndex += currentExpectedRequestLength;
        accessSafely.writeUsing(ID_REQUESTS, request);

        final ConsumerByteBuffer responseBuffer = new BasicConsumerByteBuffer(1, currentExpectedRequestLength);
        context.respondWith(responseBuffer.clear().put(request.getBytes()).flip()); // echo back

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

  public List<String> requests() {
    return accessSafely.readFrom(ID_REQUESTS);
  }
}
