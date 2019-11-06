// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.channel;

import java.io.IOException;

public interface ChannelReader {
  void close();
  String name();
  int port();
  void openFor(final ChannelReaderConsumer consumer) throws IOException;
  void probeChannel();
}
