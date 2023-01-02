// Copyright © 2012-2023 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.xoom.wire.fdx.outbound;

import java.util.Collection;
import java.util.Map;

import io.vlingo.xoom.wire.node.Id;
import io.vlingo.xoom.wire.node.Node;

public interface ManagedOutboundChannelProvider {
  Map<Id, ManagedOutboundChannel> allOtherNodeChannels();
  ManagedOutboundChannel channelFor(final Node node);
  Map<Id, ManagedOutboundChannel> channelsFor(final Collection<Node> nodes);
  void close();
  void close(final Id id);
}
