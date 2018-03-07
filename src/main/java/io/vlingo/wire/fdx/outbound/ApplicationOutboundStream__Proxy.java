// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.fdx.outbound;

import java.util.function.Consumer;

import io.vlingo.actors.Actor;
import io.vlingo.actors.DeadLetter;
import io.vlingo.actors.LocalMessage;
import io.vlingo.actors.Mailbox;
import io.vlingo.wire.message.RawMessage;
import io.vlingo.wire.node.Id;

public class ApplicationOutboundStream__Proxy implements ApplicationOutboundStream {
  private static final String representationStop1 = "stop()";
  private static final String representationBroadcast2 = "broadcast(RawMessage)";
  private static final String representationSendTo3 = "sendTo(RawMessage, Id)";

  private final Actor actor;
  private final Mailbox mailbox;

  public ApplicationOutboundStream__Proxy(final Actor actor, final Mailbox mailbox) {
    this.actor = actor;
    this.mailbox = mailbox;
  }
  
  @Override
  public boolean isStopped() {
    return actor.isStopped();
  }

  @Override
  public void stop() {
    if (!actor.isStopped()) {
      final Consumer<ApplicationOutboundStream> consumer = (actor) -> actor.stop();
      mailbox.send(new LocalMessage<ApplicationOutboundStream>(actor, ApplicationOutboundStream.class, consumer, representationStop1));
    } else {
      actor.deadLetters().failedDelivery(new DeadLetter(actor, representationStop1));
    }
  }

  @Override
  public void broadcast(final RawMessage message) {
    if (!actor.isStopped()) {
      final Consumer<ApplicationOutboundStream> consumer = (actor) -> actor.broadcast(message);
      mailbox.send(new LocalMessage<ApplicationOutboundStream>(actor, ApplicationOutboundStream.class, consumer, representationBroadcast2));
    } else {
      actor.deadLetters().failedDelivery(new DeadLetter(actor, representationBroadcast2));
    }
  }

  @Override
  public void sendTo(final RawMessage message, final Id targetId) {
    if (!actor.isStopped()) {
      final Consumer<ApplicationOutboundStream> consumer = (actor) -> actor.sendTo(message, targetId);
      mailbox.send(new LocalMessage<ApplicationOutboundStream>(actor, ApplicationOutboundStream.class, consumer, representationSendTo3));
    } else {
      actor.deadLetters().failedDelivery(new DeadLetter(actor, representationSendTo3));
    }
  }
}
