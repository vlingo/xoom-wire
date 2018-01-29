// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.message;

public class PublisherAvailability {
  private final String host;
  private final String name;
  private final int port;
  
  public PublisherAvailability(final String name, final String host, final int port) {
    this.name = name;
    this.host = host;
    this.port = port;
  }
  
  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    
    builder
      .append("PUB\n")
      .append("nm=").append(name)
      .append(" addr=").append(host).append(":").append(port);
    
    return builder.toString();
  }
}
