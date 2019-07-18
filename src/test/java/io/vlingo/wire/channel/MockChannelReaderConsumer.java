// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.channel;

import java.util.ArrayList;
import java.util.List;

import io.vlingo.actors.testkit.AccessSafely;
import io.vlingo.wire.channel.ChannelReaderConsumer;
import io.vlingo.wire.message.RawMessage;

public class MockChannelReaderConsumer implements ChannelReaderConsumer {
  private AccessSafely access = AccessSafely.afterCompleting(0);
  public int consumeCount;
  public List<String> messages = new ArrayList<>();
  
  @Override
  public void consume(final RawMessage message) {
    access.writeUsing("add", message.asTextMessage() );
  }
  
  public AccessSafely afterCompleting( final int times )
  {
    access =
            AccessSafely
              .afterCompleting( times )
              .writingWith("add", (value) -> { ++consumeCount; messages.add( (String)value ); } )
              .readingWith("consumeCount", () -> consumeCount )
              .readingWith("message", (index) -> messages.get( (int)index ));
    return access;
  }
}
