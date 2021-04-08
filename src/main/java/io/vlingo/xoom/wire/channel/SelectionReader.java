// Copyright © 2012-2020 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.xoom.wire.channel;

import java.io.IOException;
import java.nio.channels.Channel;
import java.nio.channels.SelectionKey;

public abstract class SelectionReader {
  protected final ChannelMessageDispatcher dispatcher;
  protected final SelectionKey key;
  
  public SelectionReader(final ChannelMessageDispatcher dispatcher, final SelectionKey key) {
    this.dispatcher = dispatcher;
    this.key = key;
  }

  public abstract void read() throws IOException;
  
  protected void closeClientResources(final Channel channel) throws IOException {
    channel.close();
    key.cancel();
  }
}
