// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.channel;

import java.util.ArrayList;
import java.util.List;

import io.vlingo.wire.channel.ChannelReaderConsumer;
import io.vlingo.wire.message.RawMessage;

public class MockChannelReaderConsumer implements ChannelReaderConsumer {
  public int consumeCount;
  public List<String> messages = new ArrayList<>();
  
  @Override
  public void consume(final RawMessage message) {
    ++consumeCount;
    messages.add(message.asTextMessage());
  }
}
