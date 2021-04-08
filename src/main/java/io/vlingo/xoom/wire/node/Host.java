// Copyright © 2012-2020 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.xoom.wire.node;

public final class Host {
  public static final String NO_NAME = "?";
  public static final Host NO_HOST_NAME = new Host(NO_NAME);

  private final String name;

  public static Host of(final String name) {
    return new Host(name);
  }

  public Host(final String name) {
    this.name = name;
  }

  public final String name() {
    return name;
  }

  public boolean hasNoName() {
    return name().equals(NO_NAME);
  }

  public boolean sameAs(final String name) {
    return name.equals(name);
  }
  
  @Override
  public boolean equals(final Object other) {
    if (other == null || other.getClass() != Host.class) {
      return false;
    }

    return this.name.equals(((Host) other).name);
  }

  @Override
  public int hashCode() {
    return 31 * name.hashCode();
  }

  @Override
  public String toString() {
    return "Host[" + name + "]";
  }
}
