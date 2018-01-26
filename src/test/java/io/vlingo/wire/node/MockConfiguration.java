// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.node;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

import io.vlingo.actors.Logger;
import io.vlingo.wire.node.Configuration;
import io.vlingo.wire.node.Host;
import io.vlingo.wire.node.Id;
import io.vlingo.wire.node.Name;
import io.vlingo.wire.node.Node;

public class MockConfiguration implements Configuration {
  private final Set<Node> nodes;

  public MockConfiguration() {
    final Node node1 = Node.with(Id.of(1), Name.of("node1"), Host.of("localhost"), 37371, 37372);
    final Node node2 = Node.with(Id.of(2), Name.of("node2"), Host.of("localhost"), 37373, 37374);
    final Node node3 = Node.with(Id.of(3), Name.of("node3"), Host.of("localhost"), 37375, 37376);
    
    this.nodes = new TreeSet<Node>(Arrays.asList(node1, node2, node3));
  }

  public Set<Node> allNodes() {
    return Collections.unmodifiableSet(nodes);
  }

  @Override
  public Set<Node> allNodesOf(final Collection<Id> ids) {
    final Set<Node> nodes = new TreeSet<Node>();
    
    return nodes;
  }

  public final Set<Node> allOtherNodes(final Id nodeId) {
    final Set<Node> except = new TreeSet<Node>();

    for (final Node node : nodes) {
      if (!node.id().equals(nodeId)) {
        except.add(node);
      }
    }

    return except;
  }

  @Override
  public Set<Id> allOtherNodesId(final Id nodeId) {
    final Set<Id> ids = new TreeSet<Id>();
    
    for (final Node node : allOtherNodes(nodeId)) {
      ids.add(node.id());
    }
    
    return ids;
  }

  public final Set<Node> allGreaterNodes(final Id nodeId) {
    final Set<Node> greater = new TreeSet<Node>();

    for (final Node node : nodes) {
      if (node.id().greaterThan(nodeId)) {
        greater.add(node);
      }
    }

    return greater;
  }

  public Set<String> allNodeNames() {
    final Set<String> names = new TreeSet<String>();

    for (final Node node : nodes) {
      names.add(node.name().value());
    }

    return names;
  }

  public final Node nodeMatching(final Id nodeId) {
    for (final Node node : nodes) {
      if (node.id().equals(nodeId)) {
        return node;
      }
    }
    return Node.NO_NODE;
  }

  public final Id greatestNodeId() {
    Id greatest = Id.NO_ID;

    for (final Node node : nodes) {
      if (node.id().greaterThan(greatest)) {
        greatest = node.id();
      }
    }

    return greatest;
  }

  public boolean hasNode(final Id nodeId) {
    for (final Node node : nodes) {
      if (node.id().equals(nodeId)) {
        return true;
      }
    }
    return false;
  }

  public int totalNodes() {
    return nodes.size();
  }

  @Override
  public Logger logger() {
    return null;
  }

  @Override
  public boolean equals(final Object other) {
    if (other == null || other.getClass() != MockConfiguration.class) {
      return false;
    }

    return this.nodes.equals(((MockConfiguration) other).nodes);
  }

  @Override
  public int hashCode() {
    return 31 * nodes.hashCode();
  }

  @Override
  public String toString() {
    return "MockConfiguration[" + nodes + "]";
  }
}
