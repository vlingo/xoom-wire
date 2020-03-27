// Copyright © 2012-2020 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.channel;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import io.vlingo.actors.testkit.AccessSafely;
import io.vlingo.wire.message.RawMessage;

public class MockChannelReaderConsumer implements ChannelReaderConsumer {
  private AccessSafely access = AccessSafely.afterCompleting(0);
  public AtomicInteger consumeCount = new AtomicInteger(0);
  public List<String> messages = new CopyOnWriteArrayList<>();

  @Override
  public void consume(final RawMessage message) {
    access.writeUsing("add", message.asTextMessage() );
  }

  public AccessSafely afterCompleting( final int times )
  {
    access =
            AccessSafely
              .afterCompleting( times )
              .writingWith("add", (String value) -> { consumeCount.incrementAndGet(); messages.add(value); })
              .readingWith("consumeCount", () -> consumeCount.get())
              .readingWith("message", (Integer index) -> messages.get(index));
    return access;
  }

  public int getConsumeCount(){
   return access.readFrom("consumeCount");
  }


  public String getMessage(int index){
   return access.readFrom("message", index);
  }
}
