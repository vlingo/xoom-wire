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

public class RequestChannelConsumer__Proxy implements RequestChannelConsumer {
  private static final String representationConsume1 = "consume(RequestResponseContext<?>)";
  private final Actor actor;
  private final Mailbox mailbox;

  public RequestChannelConsumer__Proxy(final Actor actor, final Mailbox mailbox) {
    this.actor = actor;
    this.mailbox = mailbox;
  }

  @Override
  public void consume(final RequestResponseContext<?> context) {
    if (!actor.isStopped()) {
      final Consumer<RequestChannelConsumer> consumer = (actor) -> actor.consume(context);
      mailbox.send(new LocalMessage<RequestChannelConsumer>(actor, RequestChannelConsumer.class, consumer, representationConsume1));
    } else {
      actor.deadLetters().failedDelivery(new DeadLetter(actor, representationConsume1));
    }
  }
}
