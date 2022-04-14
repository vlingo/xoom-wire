// Copyright © 2012-2022 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.xoom.wire.node;

import java.util.Collection;
import java.util.Set;

import io.vlingo.xoom.actors.Logger;

public interface Configuration {
  Set<Node> allNodes();
  Set<Node> allNodesOf(final Collection<Id> ids);
  Set<Node> allGreaterNodes(final Id id);
  Set<Node> allOtherNodes(final Id id);
  Set<Id> allOtherNodesId(final Id id);
  Set<String> allNodeNames();
  Node nodeMatching(final Id id);
  Id greatestNodeId();
  boolean hasNode(final Id id);
  int totalNodes();
  Logger logger();
}
