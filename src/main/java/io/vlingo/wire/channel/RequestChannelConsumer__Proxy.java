// Copyright © 2012-2020 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.channel;

import io.vlingo.actors.Actor;
import io.vlingo.actors.DeadLetter;
import io.vlingo.actors.LocalMessage;
import io.vlingo.actors.Mailbox;
import io.vlingo.common.SerializableConsumer;
import io.vlingo.wire.message.ConsumerByteBuffer;

public class RequestChannelConsumer__Proxy implements RequestChannelConsumer {
  private static final String representationCloseWith1 = "closeWith(RequestResponseContext<?>, Object)";
  private static final String representationConsume2 = "consume(RequestResponseContext<?>, ConsumerByteBuffer)";
  private final Actor actor;
  private final Mailbox mailbox;

  public RequestChannelConsumer__Proxy(final Actor actor, final Mailbox mailbox) {
    this.actor = actor;
    this.mailbox = mailbox;
  }

  @Override
  public void closeWith(final RequestResponseContext<?> context, final Object data) {
    if (!actor.isStopped()) {
      final SerializableConsumer<RequestChannelConsumer> consumer = (actor) -> actor.closeWith(context, data);
      mailbox.send(new LocalMessage<RequestChannelConsumer>(actor, RequestChannelConsumer.class, consumer, representationCloseWith1));
    } else {
      actor.deadLetters().failedDelivery(new DeadLetter(actor, representationCloseWith1));
    }
  }

  @Override
  public void consume(final RequestResponseContext<?> context, final ConsumerByteBuffer buffer) {
    if (!actor.isStopped()) {
      final SerializableConsumer<RequestChannelConsumer> consumer = (actor) -> actor.consume(context, buffer);
      mailbox.send(new LocalMessage<RequestChannelConsumer>(actor, RequestChannelConsumer.class, consumer, representationConsume2));
    } else {
      actor.deadLetters().failedDelivery(new DeadLetter(actor, representationConsume2));
    }
  }
}
