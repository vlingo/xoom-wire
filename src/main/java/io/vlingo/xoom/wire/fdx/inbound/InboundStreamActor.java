// Copyright © 2012-2023 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.xoom.wire.fdx.inbound;

import io.vlingo.xoom.actors.Actor;
import io.vlingo.xoom.common.Cancellable;
import io.vlingo.xoom.common.Scheduled;
import io.vlingo.xoom.wire.channel.ChannelReader;
import io.vlingo.xoom.wire.channel.ChannelReaderConsumer;
import io.vlingo.xoom.wire.message.RawMessage;
import io.vlingo.xoom.wire.node.AddressType;

public class InboundStreamActor extends Actor implements InboundStream, ChannelReaderConsumer, Scheduled<Object> {
  private final AddressType addressType;
  private Cancellable cancellable;
  private final InboundStreamInterest interest;
  private final long probeInterval;
  private final ChannelReader reader;

  public InboundStreamActor(
          final InboundStreamInterest interest,
          final AddressType addressType,
          final ChannelReader reader,
          final long probeInterval) {
    this.interest = interest;
    this.addressType = addressType;
    this.reader = reader;
    this.probeInterval = probeInterval;
  }
  
  //=========================================
  // Scheduled
  //=========================================

  @Override
  public void intervalSignal(final Scheduled<Object> scheduled, final Object data) {
    reader.probeChannel();
  }

  //=========================================
  // Startable
  //=========================================

  @Override
  @SuppressWarnings("unchecked")
  public void start() {
    if (isStopped()) return;
    
    logger().debug("Inbound stream listening: for '" + reader.name() + "'");
    
    try {
      reader.openFor(this);
    } catch (Exception e) {
      reader.close();
      e.printStackTrace();
      throw new IllegalStateException(e.getMessage(), e);
    }
    cancellable = this.stage().scheduler().schedule(selfAs(Scheduled.class), null, 1000, probeInterval);
  }

  //=========================================
  // Stoppable
  //=========================================

  @Override
  public void stop() {
    if (cancellable != null) {
      cancellable.cancel();
      cancellable = null;
    }
    
    if (reader != null) {
      reader.close();
    }
    
    super.stop();
  }

  //=========================================
  // InboundReaderConsumer
  //=========================================
  
  @Override
  public void consume(final RawMessage message) {
    interest.handleInboundStreamMessage(addressType, RawMessage.copy(message));
  }
}
