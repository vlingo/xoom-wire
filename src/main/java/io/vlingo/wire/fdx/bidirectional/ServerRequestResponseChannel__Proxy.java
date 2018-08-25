package io.vlingo.wire.fdx.bidirectional;

import java.util.function.Consumer;

import io.vlingo.actors.Actor;
import io.vlingo.actors.DeadLetter;
import io.vlingo.actors.LocalMessage;
import io.vlingo.actors.Mailbox;

public class ServerRequestResponseChannel__Proxy implements ServerRequestResponseChannel {
  private static final String representationStop1 = "stop()";
  private static final String representationClose2 = "close()";

  private final Actor actor;
  private final Mailbox mailbox;

  public ServerRequestResponseChannel__Proxy(final Actor actor, final Mailbox mailbox) {
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
      final Consumer<ServerRequestResponseChannel> consumer = (actor) -> actor.stop();
      mailbox.send(new LocalMessage<ServerRequestResponseChannel>(actor, ServerRequestResponseChannel.class, consumer, representationStop1));
    } else {
      actor.deadLetters().failedDelivery(new DeadLetter(actor, representationStop1));
    }
  }

  @Override
  public void close() {
    if (!actor.isStopped()) {
      final Consumer<ServerRequestResponseChannel> consumer = (actor) -> actor.close();
      mailbox.send(new LocalMessage<ServerRequestResponseChannel>(actor, ServerRequestResponseChannel.class, consumer, representationClose2));
    } else {
      actor.deadLetters().failedDelivery(new DeadLetter(actor, representationClose2));
    }
  }
}
