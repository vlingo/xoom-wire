// Copyright © 2012-2023 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.xoom.wire.fdx.inbound;

import io.vlingo.xoom.actors.Actor;
import io.vlingo.xoom.actors.DeadLetter;
import io.vlingo.xoom.actors.LocalMessage;
import io.vlingo.xoom.actors.Mailbox;
import io.vlingo.xoom.common.SerializableConsumer;
import io.vlingo.xoom.wire.message.RawMessage;
import io.vlingo.xoom.wire.node.AddressType;

public class InboundStreamInterest__Proxy implements InboundStreamInterest {
  private final Actor actor;
  private final Mailbox mailbox;

  public InboundStreamInterest__Proxy(final Actor actor, final Mailbox mailbox) {
    this.actor = actor;
    this.mailbox = mailbox;
  }
  
  @Override
  public void handleInboundStreamMessage(final AddressType addressType, final RawMessage message) {
    if (!actor.isStopped()) {
      final SerializableConsumer<InboundStreamInterest> consumer = (actor) -> actor.handleInboundStreamMessage(addressType, message);
      mailbox.send(new LocalMessage<InboundStreamInterest>(actor, InboundStreamInterest.class, consumer, "handleInboundStreamMessage(AddressType, RawMessage)"));
    } else {
      actor.deadLetters().failedDelivery(new DeadLetter(actor, "handleInboundStreamMessage(AddressType, RawMessage)"));
    }
  }
}
