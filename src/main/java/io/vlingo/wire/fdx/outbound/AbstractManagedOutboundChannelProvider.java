// Copyright © 2012-2020 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.
package io.vlingo.wire.fdx.outbound;

import io.vlingo.wire.node.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public abstract class AbstractManagedOutboundChannelProvider implements ManagedOutboundChannelProvider {

  private static final Logger logger = LoggerFactory.getLogger(AbstractManagedOutboundChannelProvider.class);

  protected static Address addressOf(final Node node, final AddressType type) {
    logger.debug("addressOf {}", node);
    return (type == AddressType.OP ? node.operationalAddress() : node.applicationAddress());
  }

  private final Configuration configuration;
  private final Node node;
  private final Map<Id, ManagedOutboundChannel> nodeChannels = new HashMap<>();
  private final AddressType type;

  protected AbstractManagedOutboundChannelProvider(final Node node, final AddressType type, final Configuration configuration) {
    this.configuration = configuration;
    this.node = node;
    this.type = type;
  }

  @Override
  public Map<Id, ManagedOutboundChannel> allOtherNodeChannels() {
    return channelsFor(configuration.allOtherNodes(node.id()));
  }

  @Override
  public ManagedOutboundChannel channelFor(final Id id) {
    final ManagedOutboundChannel channel = nodeChannels.get(id);

    if (channel != null) {
      return channel;
    }

    final ManagedOutboundChannel unopenedChannel = unopenedChannelFor(configuration.nodeMatching(id), configuration, type);

    nodeChannels.put(id, unopenedChannel);

    return unopenedChannel;
  }

  @Override
  public Map<Id, ManagedOutboundChannel> channelsFor(final Collection<Node> nodes) {
    final Map<Id, ManagedOutboundChannel> channels = new TreeMap<Id, ManagedOutboundChannel>();

    for (final Node node : nodes) {
      ManagedOutboundChannel channel = nodeChannels.get(node.id());

      if (channel == null) {
        channel = unopenedChannelFor(node, configuration, type);
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

  @Override
  public void configureKnownChannels() {
    for (final Node node : configuration.allOtherNodes(node.id())) {
      nodeChannels.put(node.id(), unopenedChannelFor(node, configuration, type));
    }
  }

  protected abstract ManagedOutboundChannel unopenedChannelFor(final Node node, final Configuration configuration, final AddressType type);

}
