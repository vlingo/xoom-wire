// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.fdx.inbound;

import java.util.function.Consumer;

import io.vlingo.actors.Actor;
import io.vlingo.actors.DeadLetter;
import io.vlingo.actors.LocalMessage;
import io.vlingo.actors.Mailbox;

public class InboundStream__Proxy implements InboundStream {
  private final Actor actor;
  private final Mailbox mailbox;

  public InboundStream__Proxy(final Actor actor, final Mailbox mailbox) {
    this.actor = actor;
    this.mailbox = mailbox;
  }

  @Override
  public void start() {
    if (!actor.isStopped()) {
      final Consumer<InboundStream> consumer = (actor) -> actor.start();
      mailbox.send(new LocalMessage<InboundStream>(actor, InboundStream.class, consumer, "start()"));
    } else {
      actor.deadLetters().failedDelivery(new DeadLetter(actor, "start()"));
    }
  }

  @Override
  public boolean isStopped() {
    return actor.isStopped();
  }

  @Override
  public void stop() {
    if (!actor.isStopped()) {
      final Consumer<InboundStream> consumer = (actor) -> actor.stop();
      mailbox.send(new LocalMessage<InboundStream>(actor, InboundStream.class, consumer, "stop()"));
    } else {
      actor.deadLetters().failedDelivery(new DeadLetter(actor, "stop()"));
    }
  }
}
