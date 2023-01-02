// Copyright © 2012-2023 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.xoom.wire.multicast;

public final class Group {
  private final String address;
  private final int port;
  
  public Group(final String address, final int port) {
    this.address = address;
    this.port = port;
  }
  
  public String address() {
    return address;
  }
  
  public int port() {
    return port;
  }
}
