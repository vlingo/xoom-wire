// Copyright © 2012-2023 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.
package io.vlingo.xoom.wire.fdx.outbound;

import io.vlingo.xoom.actors.Logger;
import io.vlingo.xoom.wire.node.*;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public abstract class AbstractManagedOutboundChannelProvider implements ManagedOutboundChannelProvider {
  private final Node localNode;
  private final AddressType addressType;
  private final Logger logger;

  private final Map<Id, ManagedOutboundChannel> nodeChannels = new HashMap<>(); // live channels created on demand

  protected AbstractManagedOutboundChannelProvider(final Node localNode, final AddressType type, final Logger logger) {
    this.localNode = localNode;
    this.addressType = type;
    this.logger = logger;
  }

  @Override
  public Map<Id, ManagedOutboundChannel> allOtherNodeChannels() {
    return nodeChannels.entrySet().stream()
            .filter(entry -> !entry.getKey().equals(localNode.id()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  @Override
  public ManagedOutboundChannel channelFor(final Node node) {
    final ManagedOutboundChannel channel = nodeChannels.get(node.id());

    if (channel != null) {
      return channel;
    }

    final Address nodeAddress = addressOf(node, addressType);
    final ManagedOutboundChannel unopenedChannel = unopenedChannelFor(node, nodeAddress, logger);

    nodeChannels.put(node.id(), unopenedChannel);

    return unopenedChannel;
  }

  @Override
  public Map<Id, ManagedOutboundChannel> channelsFor(final Collection<Node> nodes) {
    final Map<Id, ManagedOutboundChannel> channels = new TreeMap<>();

    for (final Node node : nodes) {
      ManagedOutboundChannel channel = nodeChannels.get(node.id());

      if (channel == null) {
        final Address nodeAddress = addressOf(node, addressType);
        channel = unopenedChannelFor(node, nodeAddress, logger);
        nodeChannels.put(node.id(), channel);
      }

      channels.put(node.id(), channel);
    }

    return channels;
  }

  @Override
  public void close() {
    for (final ManagedOutboundChannel channel : nodeChannels.values()) {
      channel.close();
    }

    nodeChannels.clear();
  }

  @Override
  public void close(final Id id) {
    final ManagedOutboundChannel channel = nodeChannels.remove(id);

    if (channel != null) {
      channel.close();
    }
  }

  protected Address addressOf(final Node node, final AddressType type) {
    logger.debug("addressOf {}", node);
    return (type == AddressType.OP ? node.operationalAddress() : node.applicationAddress());
  }

  protected abstract ManagedOutboundChannel unopenedChannelFor(final Node node, final Address nodeAddress, final Logger logger);
}
