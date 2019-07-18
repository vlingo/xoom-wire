// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.multicast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import io.vlingo.actors.Logger;
import io.vlingo.actors.testkit.AccessSafely;
import io.vlingo.wire.channel.MockChannelReaderConsumer;
import org.junit.Test;

public class MulticastTest {

  @Test
  public void testMulticastPublishSubscribe() throws Exception {
    final MockChannelReaderConsumer publisherConsumer = new MockChannelReaderConsumer();
    final AccessSafely consumerAccess = publisherConsumer.afterCompleting(0);
    
    final MulticastPublisherReader publisher =
            new MulticastPublisherReader(
                    "test-publisher",
                    new Group("237.37.37.1", 37371),
                    37379,
                    1024,
                    publisherConsumer,
                    Logger.basicLogger());
    
    final MulticastSubscriber subscriber =
            new MulticastSubscriber(
                    "test-subscriber",
                    new Group("237.37.37.1", 37371),
                    1024,
                    10,
                    Logger.basicLogger());
    
    final MockChannelReaderConsumer subscriberConsumer = new MockChannelReaderConsumer();
    final AccessSafely subscriberAccess = subscriberConsumer.afterCompleting(1);
    subscriber.openFor(subscriberConsumer);
    
    for (int idx = 0; idx < 10; ++idx) {
      publisher.sendAvailability();
    }
    
    publisher.processChannel();
    
    for (int i = 0; i < 10; ++i) {
      subscriber.probeChannel();
    }
    
    assertEquals( 0, (int)consumerAccess.readFrom( "consumeCount" ));
    assertTrue( ((int)subscriberAccess.readFrom( "consumeCount" )) >= 1 );
  }
  
}
