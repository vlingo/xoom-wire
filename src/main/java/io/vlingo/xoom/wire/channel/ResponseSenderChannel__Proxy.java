// Copyright © 2012-2023 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.xoom.wire.channel;

import io.vlingo.xoom.actors.Actor;
import io.vlingo.xoom.actors.DeadLetter;
import io.vlingo.xoom.actors.LocalMessage;
import io.vlingo.xoom.actors.Mailbox;
import io.vlingo.xoom.common.SerializableConsumer;
import io.vlingo.xoom.wire.message.ConsumerByteBuffer;

public class ResponseSenderChannel__Proxy implements ResponseSenderChannel {
  private static final String representationAbondon1 = "abandon(RequestResponseContext<?>)";
  private static final String representationRespondWith2 = "respondWith(RequestResponseContext<?>, ConsumerByteBuffer, boolean)";

  private final Actor actor;
  private final Mailbox mailbox;

  public ResponseSenderChannel__Proxy(final Actor actor, final Mailbox mailbox) {
    this.actor = actor;
    this.mailbox = mailbox;
  }

  @Override
  public void abandon(final RequestResponseContext<?> context) {
    if (!actor.isStopped()) {
      final SerializableConsumer<ResponseSenderChannel> consumer = (actor) -> actor.abandon(context);
      mailbox.send(new LocalMessage<ResponseSenderChannel>(actor, ResponseSenderChannel.class, consumer, representationAbondon1));
    } else {
      actor.deadLetters().failedDelivery(new DeadLetter(actor, representationAbondon1));
    }
  }

  @Override
  public void respondWith(RequestResponseContext<?> context, final ConsumerByteBuffer buffer) {
    respondWith(context, buffer, false);
  }

  @Override
  public void respondWith(final RequestResponseContext<?> context, final ConsumerByteBuffer buffer, final boolean closeFollowing) {
    if (!actor.isStopped()) {
      final SerializableConsumer<ResponseSenderChannel> consumer = (actor) -> actor.respondWith(context, buffer, closeFollowing);
      mailbox.send(new LocalMessage<ResponseSenderChannel>(actor, ResponseSenderChannel.class, consumer, representationRespondWith2));
    } else {
      actor.deadLetters().failedDelivery(new DeadLetter(actor, representationRespondWith2));
    }
  }

  @Override
  public void respondWith(final RequestResponseContext<?> context, final Object response, final boolean closeFollowing) {
    if (!actor.isStopped()) {
      final SerializableConsumer<ResponseSenderChannel> consumer = (actor) -> actor.respondWith(context, response, closeFollowing);
      mailbox.send(new LocalMessage<ResponseSenderChannel>(actor, ResponseSenderChannel.class, consumer, representationRespondWith2));
    } else {
      actor.deadLetters().failedDelivery(new DeadLetter(actor, representationRespondWith2));
    }
  }
}
