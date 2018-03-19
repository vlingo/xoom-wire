package io.vlingo.wire.fdx.bidirectional;

import java.util.function.Consumer;

import io.vlingo.actors.Actor;
import io.vlingo.actors.DeadLetter;
import io.vlingo.actors.LocalMessage;
import io.vlingo.actors.Mailbox;
import io.vlingo.wire.channel.RequestResponseContext;
import io.vlingo.wire.message.ConsumerByteBuffer;

public class ServerRequestResponseChannel__Proxy implements ServerRequestResponseChannel {
  private static final String representationAbondon1 = "abandon(RequestResponseContext<?>)";
  private static final String representationRespondWith2 = "respondWith(RequestResponseContext<?>, ConsumerByteBuffer)";
  private static final String representationStop3 = "stop()";
  private static final String representationClose4 = "close()";

  private final Actor actor;
  private final Mailbox mailbox;

  public ServerRequestResponseChannel__Proxy(final Actor actor, final Mailbox mailbox) {
    this.actor = actor;
    this.mailbox = mailbox;
  }

  @Override
  public void abandon(final RequestResponseContext<?> context) {
    if (!actor.isStopped()) {
      final Consumer<ServerRequestResponseChannel> consumer = (actor) -> actor.abandon(context);
      mailbox.send(new LocalMessage<ServerRequestResponseChannel>(actor, ServerRequestResponseChannel.class, consumer, representationAbondon1));
    } else {
      actor.deadLetters().failedDelivery(new DeadLetter(actor, representationAbondon1));
    }
  }

  @Override
  public void respondWith(final RequestResponseContext<?> context, final ConsumerByteBuffer buffer) {
    if (!actor.isStopped()) {
      final Consumer<ServerRequestResponseChannel> consumer = (actor) -> actor.respondWith(context, buffer);
      mailbox.send(new LocalMessage<ServerRequestResponseChannel>(actor, ServerRequestResponseChannel.class, consumer, representationRespondWith2));
    } else {
      actor.deadLetters().failedDelivery(new DeadLetter(actor, representationRespondWith2));
    }
  }

  @Override
  public boolean isStopped() {
    return actor.isStopped();
  }

  @Override
  public void stop() {
    if (!actor.isStopped()) {
      final Consumer<ServerRequestResponseChannel> consumer = (actor) -> actor.stop();
      mailbox.send(new LocalMessage<ServerRequestResponseChannel>(actor, ServerRequestResponseChannel.class, consumer, representationStop3));
    } else {
      actor.deadLetters().failedDelivery(new DeadLetter(actor, representationStop3));
    }
  }

  @Override
  public void close() {
    if (!actor.isStopped()) {
      final Consumer<ServerRequestResponseChannel> consumer = (actor) -> actor.close();
      mailbox.send(new LocalMessage<ServerRequestResponseChannel>(actor, ServerRequestResponseChannel.class, consumer, representationClose4));
    } else {
      actor.deadLetters().failedDelivery(new DeadLetter(actor, representationClose4));
    }
  }
}
