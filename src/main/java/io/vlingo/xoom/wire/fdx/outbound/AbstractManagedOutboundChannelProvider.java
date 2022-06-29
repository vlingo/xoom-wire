// Copyright © 2012-2022 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.
package io.vlingo.xoom.wire.fdx.outbound;

import io.vlingo.xoom.actors.Logger;
import io.vlingo.xoom.wire.node.*;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public abstract class AbstractManagedOutboundChannelProvider implements ManagedOutboundChannelProvider {

  private static final org.slf4j.Logger logger = LoggerFactory.getLogger(AbstractManagedOutboundChannelProvider.class);

  protected static Address addressOf(final Node node, final AddressType type) {
    logger.debug("addressOf {}", node);
    return (type == AddressType.OP ? node.operationalAddress() : node.applicationAddress());
  }

  private final Configuration configuration;
  private final Node localNode;
  private final Map<Id, Address> allAddresses; // all addresses provided by configuration
  private final Map<Id, ManagedOutboundChannel> nodeChannels = new HashMap<>(); // live channels created on demand

  protected AbstractManagedOutboundChannelProvider(final Node localNode, final AddressType type, final Configuration configuration) {
    this.configuration = configuration;
    this.localNode = localNode;
    this.allAddresses = new HashMap<>();
    for (final Node node : configuration.allNodes()) { // exclude local node?
      allAddresses.put(node.id(), addressOf(node, type));
    }
  }

  protected AbstractManagedOutboundChannelProvider(final Node localNode, final AddressType type, final Configuration configuration, boolean createEagerly) {
    this(localNode, type, configuration);
    if (createEagerly) {
      allAddresses.keySet().forEach(this::channelFor);
    }
  }

  @Override
  public Map<Id, ManagedOutboundChannel> allOtherNodeChannels() {
    return nodeChannels.entrySet().stream()
            .filter(entry -> !entry.getKey().equals(localNode.id()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  @Override
  public ManagedOutboundChannel channelFor(final Id id) {
    final ManagedOutboundChannel channel = nodeChannels.get(id);

    if (channel != null) {
      return channel;
    }

    final Address nodeAddress = this.allAddresses.get(id);
    final ManagedOutboundChannel unopenedChannel = unopenedChannelFor(configuration.nodeMatching(id), nodeAddress, configuration.logger());

    nodeChannels.put(id, unopenedChannel);

    return unopenedChannel;
  }

  @Override
  public Map<Id, ManagedOutboundChannel> channelsFor(final Collection<Node> nodes) {
    final Map<Id, ManagedOutboundChannel> channels = new TreeMap<>();

    for (final Node node : nodes) {
      ManagedOutboundChannel channel = nodeChannels.get(node.id());

      if (channel == null) {
        final Address nodeAddress = allAddresses.get(node.id());
        channel = unopenedChannelFor(node, nodeAddress, configuration.logger());
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

  protected abstract ManagedOutboundChannel unopenedChannelFor(final Node node, final Address nodeAddress, final Logger logger);
}
