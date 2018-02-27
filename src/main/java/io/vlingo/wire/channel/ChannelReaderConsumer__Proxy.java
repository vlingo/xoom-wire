// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.channel;

import java.util.function.Consumer;

import io.vlingo.actors.Actor;
import io.vlingo.actors.DeadLetter;
import io.vlingo.actors.LocalMessage;
import io.vlingo.actors.Mailbox;
import io.vlingo.wire.message.RawMessage;

public class ChannelReaderConsumer__Proxy implements ChannelReaderConsumer {
  private final Actor actor;
  private final Mailbox mailbox;

  public ChannelReaderConsumer__Proxy(final Actor actor, final Mailbox mailbox) {
    this.actor = actor;
    this.mailbox = mailbox;
  }

  @Override
  public void consume(final RawMessage message) {
    if (!actor.isStopped()) {
      final Consumer<ChannelReaderConsumer> consumer = (actor) -> actor.consume(message);
      mailbox.send(new LocalMessage<ChannelReaderConsumer>(actor, ChannelReaderConsumer.class, consumer, "consume(RawMessage)"));
    } else {
      actor.deadLetters().failedDelivery(new DeadLetter(actor, "consume(RawMessage)"));
    }
  }
}
