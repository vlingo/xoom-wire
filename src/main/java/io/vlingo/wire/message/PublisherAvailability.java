// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.message;

import io.vlingo.wire.node.Address;
import io.vlingo.wire.node.AddressType;
import io.vlingo.wire.node.Host;
import io.vlingo.wire.node.Name;

public class PublisherAvailability {
  public static final String TypeName = "PUB";
  
  private final String host;
  private final String name;
  private final int port;
  
  public static PublisherAvailability from(final String content) {
    if (content.startsWith(TypeName)) {
      final Name name = MessagePartsBuilder.nameFrom(content);
      final AddressType type = AddressType.MAIN;
      final Address address = MessagePartsBuilder.addressFromRecord(content, type);
      return new PublisherAvailability(name.value(), address.host().name(), address.port());
    }
    return new PublisherAvailability(Name.NO_NAME, "", 0);
  }

  public PublisherAvailability(final String name, final String host, final int port) {
    this.name = name;
    this.host = host;
    this.port = port;
  }

  public boolean isValid() {
    return !name.equals(Name.NO_NAME);
  }

  public Address toAddress() {
    return toAddress(AddressType.MAIN);
  }

  public Address toAddress(final AddressType type) {
    return Address.from(Host.of(host), port, type);
  }

  @Override
  public int hashCode() {
    return 31 * (name.hashCode() + host.hashCode() + port);
  }

  @Override
  public boolean equals(final Object other) {
    if (other == null || other.getClass() != PublisherAvailability.class) {
      return false;
    }
    
    final PublisherAvailability otherPA = (PublisherAvailability) other;
    
    return this.name.equals(otherPA.name) && this.host.equals(otherPA.host) && this.port == otherPA.port;
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
