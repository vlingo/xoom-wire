// Copyright © 2012-2023 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.xoom.wire.channel;

import java.io.IOException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;

import io.vlingo.xoom.wire.message.RawMessageBuilder;

public class DatagramChannelSelectionReader extends SelectionReader {
  public DatagramChannelSelectionReader(final ChannelMessageDispatcher dispatcher, final SelectionKey key) {
    super(dispatcher, key);
  }

  @Override
  public void read() throws IOException {
    final DatagramChannel channel = (DatagramChannel) key.channel();
    final RawMessageBuilder builder = (RawMessageBuilder) key.attachment();

    int bytesRead = 0;
    do {
      bytesRead = channel.read(builder.workBuffer());
    } while (bytesRead > 0);

    dispatcher.dispatchMessagesFor(builder);
    
//    if (bytesRead == -1) {
//      closeClientResources(channel);
//    }
  }
}
