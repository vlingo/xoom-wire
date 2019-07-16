// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.fdx.bidirectional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.vlingo.actors.testkit.AccessSafely;
import io.vlingo.wire.channel.ResponseChannelConsumer;
import io.vlingo.wire.message.ConsumerByteBuffer;
import io.vlingo.wire.message.Converters;

public class TestSecureResponseChannelConsumer implements ResponseChannelConsumer {
  public int currentExpectedResponseLength;
  public AtomicInteger consumeCount = new AtomicInteger(0);
  public List<String> responses = new ArrayList<>();
  private AccessSafely access;

  @Override
  public void consume(final ConsumerByteBuffer buffer) {
    final String responsePart = Converters.bytesToText(buffer.array(), 0, buffer.limit());
    buffer.release();
    access.writeUsing("responses", responsePart);
  }

  public int consumeCount() {
    return access.readFrom("consumeCount");
  }

  public List<String> responses() {
    return access.readFrom("responses");
  }

  public int totalWrites() {
    return access.totalWrites();
  }

  public AccessSafely afterCompleting(final int times) {
    access = AccessSafely.afterCompleting(times);

    access.writingWith("responses", (String response) -> {
      responses.add(response);
      consumeCount.incrementAndGet();
    });

    access.readingWith("responses", () -> responses);
    access.readingWith("consumeCount", () -> consumeCount.get());

    return access;
  }
}
