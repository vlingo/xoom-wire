// Copyright © 2012-2020 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.xoom.wire.message;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.vlingo.xoom.wire.node.Address;
import io.vlingo.xoom.wire.node.AddressType;
import io.vlingo.xoom.wire.node.Host;

public class PublisherAvailabilityTest {
  private final String textMessage = "PUB\nnm=test-dir addr=1.2.3.4:111";

  @Test
  public void testMessage() {
    final PublisherAvailability publisherAvailability =
            new PublisherAvailability("test-dir", "1.2.3.4", 111);
    
    assertEquals(publisherAvailability, PublisherAvailability.from(textMessage));
    assertEquals(textMessage, publisherAvailability.toString());
  }
  
  @Test
  public void testValidity() {
    final PublisherAvailability publisherAvailability =
            new PublisherAvailability("test-dir", "1.2.3.4", 111);
    
    assertTrue(publisherAvailability.isValid());
    assertFalse(PublisherAvailability.from("blah").isValid());
    assertTrue(PublisherAvailability.from(textMessage).isValid());
  }
  
  @Test
  public void testToAddress() {
    final PublisherAvailability publisherAvailability =
            new PublisherAvailability("test-dir", "1.2.3.4", 111);
    
    assertEquals(Address.from(Host.of("1.2.3.4"), 111, AddressType.MAIN), publisherAvailability.toAddress());
    assertEquals(Address.from(Host.of("1.2.3.4"), 111, AddressType.OP), publisherAvailability.toAddress(AddressType.OP));
  }
}
