// Copyright © 2012-2022 VLINGO LABS. All rights reserved.
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

public class ResponseChannelConsumer__Proxy implements ResponseChannelConsumer {
  private static final String representationConsume1 = "consume(ByteBuffer)";

  private final Actor actor;
  private final Mailbox mailbox;

  public ResponseChannelConsumer__Proxy(final Actor actor, final Mailbox mailbox) {
    this.actor = actor;
    this.mailbox = mailbox;
  }

  @Override
  public void consume(final ConsumerByteBuffer buffer) {
    if (!actor.isStopped()) {
      final SerializableConsumer<ResponseChannelConsumer> consumer = (actor) -> actor.consume(buffer);
      mailbox.send(new LocalMessage<ResponseChannelConsumer>(actor, ResponseChannelConsumer.class, consumer, representationConsume1));
    } else {
      actor.deadLetters().failedDelivery(new DeadLetter(actor, representationConsume1));
    }
  }
}
