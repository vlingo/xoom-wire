// Copyright © 2012-2022 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.xoom.wire.node;

import java.util.Arrays;
import java.util.Collection;

public final class Node implements Comparable<Node> {
  public static Node NO_NODE =
          new Node(Id.NO_ID,
                  Name.NO_NODE_NAME,
                  false,
                  Address.NO_NODE_ADDRESS,
                  Address.NO_NODE_ADDRESS);

  public static Node with(final Id id, final Name name, final Host host, final int operationalPort, final int applicationPort) {
    return with(id, name, false, host, operationalPort, applicationPort);
  }

  public static Node with(final Id id, final Name name, final boolean seed, final Host host, final int operationalPort, final int applicationPort) {
    final Address operationalAddress = new Address(host, operationalPort, AddressType.OP);
    final Address applicationAddress = new Address(host, applicationPort, AddressType.APP);

    return new Node(id, name, seed, operationalAddress, applicationAddress);
  }

  private final Id id;
  private final Name name;
  private final Boolean seed;
  private final Address operationalAddress;
  private final Address applicationAddress;

  public Node(
      final Id id,
      final Name nodeName,
      final boolean seed,
      final Address operationalAddress,
      final Address applicationAddress) {

    this.id = id;
    this.name = nodeName;
    this.seed = seed;
    this.operationalAddress = operationalAddress;
    this.applicationAddress = applicationAddress;
  }

  public Collection<Node> collected() {
    return Arrays.asList(this);
  }

  public boolean hasMissingPart() {
    return id().hasNoId() ||
        name().hasNoName() ||
        operationalAddress().hasNoAddress() ||
        applicationAddress().hasNoAddress();
  }

  public Address applicationAddress() {
    return applicationAddress;
  }

  public Address operationalAddress() {
    return operationalAddress;
  }

  public Id id() {
    return id;
  }

  public Name name() {
    return name;
  }

  public boolean isSeed() {
    return seed;
  }

  public boolean isLeaderOver(final Id nodeId) {
    return this.isValid() && this.id().greaterThan(nodeId);
  }

  public boolean isValid() {
    return !hasMissingPart();
  }

  @Override
  public boolean equals(final Object other) {
    if (other == null || other.getClass() != Node.class) {
      return false;
    }

    final Node node = (Node) other;

    return this.id.equals(node.id)
            && this.name.equals(node.name)
            && this.seed.equals(node.seed)
            && this.operationalAddress.equals(node.operationalAddress)
            && this.applicationAddress.equals(node.applicationAddress);
  }

  @Override
  public int hashCode() {
    return 31 * (id.hashCode() + name.hashCode() + seed.hashCode() + operationalAddress.hashCode() + applicationAddress.hashCode());
  }

  @Override
  public String toString() {
    return "Node[" + id + "," + name + ", " + seed + ", " + operationalAddress + ", " + applicationAddress + "]";
  }

  public boolean greaterThan(final Node other) {
    return id.greaterThan(other.id);
  }

  public int compareTo(final Node other) {
    return this.id.compareTo(other.id);
  }
}
