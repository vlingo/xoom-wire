// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.fdx.outbound;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import io.vlingo.wire.node.Address;
import io.vlingo.wire.node.AddressType;
import io.vlingo.wire.node.Configuration;
import io.vlingo.wire.node.Id;
import io.vlingo.wire.node.Node;

public class ManagedOutboundSocketChannelProvider implements ManagedOutboundChannelProvider {
  private final Configuration configuration;
  private final Node node;
  private final Map<Id, ManagedOutboundChannel> nodeChannels;
  private final AddressType type;

  public ManagedOutboundSocketChannelProvider(
          final Node node,
          final AddressType type,
          final Configuration configuration) {
    
    this.node = node;
    this.type = type;
    this.configuration = configuration;
    this.nodeChannels = new HashMap<Id, ManagedOutboundChannel>();
    
    configureKnownChannels();
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

    final ManagedOutboundChannel unopenedChannel = unopenedChannelFor(configuration.nodeMatching(id));
    
    nodeChannels.put(id, unopenedChannel);
    
    return unopenedChannel;
  }

  @Override
  public Map<Id, ManagedOutboundChannel> channelsFor(final Collection<Node> nodes) {
    final Map<Id, ManagedOutboundChannel> channels = new TreeMap<Id, ManagedOutboundChannel>();

    for (final Node node : nodes) {
      ManagedOutboundChannel channel = nodeChannels.get(node.id());

      if (channel == null) {
        channel = unopenedChannelFor(node);
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

  private void configureKnownChannels() {
    for (final Node node : configuration.allOtherNodes(node.id())) {
      nodeChannels.put(node.id(), unopenedChannelFor(node));
    }
  }

  private ManagedOutboundChannel unopenedChannelFor(final Node node) {
    final Address address = (type == AddressType.OP ?
        node.operationalAddress() : node.applicationAddress());

    return new ManagedOutboundSocketChannel(node, address, configuration.logger());
  }
}
