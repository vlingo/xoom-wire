// Copyright © 2012-2020 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.xoom.wire.node;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AddressTest {

  @Test
  public void testAddressCreationState() {
    final Address address = Address.from(Host.of("localhost"), 11111, AddressType.OP);
    assertTrue(address.isValid());
    assertFalse(address.hasNoAddress());
    assertEquals("localhost", address.hostName());
    assertEquals(11111, address.port());
    assertEquals(AddressType.OP, address.type());
  }

  @Test
  public void testAddressFromText() {
    final Address address = Address.from("localhost:22222", AddressType.APP);
    assertTrue(address.isValid());
    assertFalse(address.hasNoAddress());
    assertEquals("localhost", address.hostName());
    assertEquals(22222, address.port());
    assertEquals(AddressType.APP, address.type());
  }
}
