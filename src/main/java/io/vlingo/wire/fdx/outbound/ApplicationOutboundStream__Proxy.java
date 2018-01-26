// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.fdx.outbound;

import java.util.function.Consumer;

import io.vlingo.actors.Actor;
import io.vlingo.actors.LocalMessage;
import io.vlingo.actors.Mailbox;
import io.vlingo.wire.message.RawMessage;
import io.vlingo.wire.node.Id;

public class ApplicationOutboundStream__Proxy implements ApplicationOutboundStream {
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
    final Consumer<ApplicationOutboundStream> consumer = (actor) -> actor.stop();
    mailbox.send(new LocalMessage<ApplicationOutboundStream>(actor, ApplicationOutboundStream.class, consumer, "stop()"));
  }

  @Override
  public void broadcast(final RawMessage message) {
    final Consumer<ApplicationOutboundStream> consumer = (actor) -> actor.broadcast(message);
    mailbox.send(new LocalMessage<ApplicationOutboundStream>(actor, ApplicationOutboundStream.class, consumer, "broadcast(RawMessage)"));
  }

  @Override
  public void sendTo(final RawMessage message, final Id targetId) {
    final Consumer<ApplicationOutboundStream> consumer = (actor) -> actor.sendTo(message, targetId);
    mailbox.send(new LocalMessage<ApplicationOutboundStream>(actor, ApplicationOutboundStream.class, consumer, "sendTo(RawMessage, Id)"));
  }
}
