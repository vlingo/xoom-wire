// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.
package io.vlingo.wire.fdx.inbound;

import io.vlingo.wire.channel.ChannelReader;

public interface InboundChannelReaderProvider {

  /**
   * Create a instance of {@code ChannelReader}
   *
   * @param port the port for read from
   * @param name the unique name of the channel
   * @return instance of {@code ChannelReader}s
   * @throws Exception if failed to create a {@code ChannelReader}
   */
  ChannelReader channelFor(final int port, final String name) throws Exception;

}
