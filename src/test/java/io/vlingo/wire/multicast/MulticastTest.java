// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.multicast;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import io.vlingo.actors.plugin.logging.jdk.JDKLogger;
import io.vlingo.wire.channel.MockChannelReaderConsumer;

public class MulticastTest {

  @Test
  public void testMulticastPublishSubscribe() throws Exception {
    final MockChannelReaderConsumer publisherConsumer = new MockChannelReaderConsumer();
    
    final MulticastPublisherReader publisher =
            new MulticastPublisherReader(
                    "test-publisher",
                    new Group("239.100.0.1", 8080),
                    1024,
                    100L,
                    publisherConsumer,
                    JDKLogger.testInstance());
    
    final MulticastSubscriber subscriber =
            new MulticastSubscriber(
                    "test-subscriber",
                    new Group("239.100.0.1", 8080),
                    1024,
                    10L,
                    JDKLogger.testInstance());
    
    final MockChannelReaderConsumer subscriberConsumer = new MockChannelReaderConsumer();
    subscriber.openFor(subscriberConsumer);
    
    for (int idx = 0; idx < 10; ++idx) {
      publisher.sendAvailability();
    }
    
    publisher.processChannel();
    
    for (int i = 0; i < 15; ++i) {
      subscriber.probeChannel();
    }
    
    assertEquals(0, publisherConsumer.consumeCount);
    assertEquals(10, subscriberConsumer.consumeCount);
  }
}
