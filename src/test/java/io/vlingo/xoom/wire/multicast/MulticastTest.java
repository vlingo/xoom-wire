// Copyright © 2012-2023 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.xoom.wire.multicast;

import io.vlingo.xoom.actors.Logger;
import io.vlingo.xoom.actors.testkit.AccessSafely;
import io.vlingo.xoom.wire.BaseWireTest;
import io.vlingo.xoom.wire.channel.MockChannelReaderConsumer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MulticastTest extends BaseWireTest {

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
