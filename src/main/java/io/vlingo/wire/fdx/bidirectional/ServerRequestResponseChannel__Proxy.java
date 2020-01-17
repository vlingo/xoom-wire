package io.vlingo.wire.fdx.bidirectional;

import io.vlingo.actors.Actor;
import io.vlingo.actors.DeadLetter;
import io.vlingo.actors.LocalMessage;
import io.vlingo.actors.Mailbox;
import io.vlingo.actors.Returns;
import io.vlingo.actors.Stoppable;
import io.vlingo.common.Completes;

import java.util.function.Consumer;

public class ServerRequestResponseChannel__Proxy implements ServerRequestResponseChannel {
  private static final String representationConclude0 = "conclude()";
  private static final String representationStop1 = "stop()";
  private static final String representationClose2 = "close()";
  private static final String representationPort = "port()";

  private final Actor actor;
  private final Mailbox mailbox;

  public ServerRequestResponseChannel__Proxy(final Actor actor, final Mailbox mailbox) {
    this.actor = actor;
    this.mailbox = mailbox;
  }

  @Override
  public void conclude() {
    if (!actor.isStopped()) {
      final Consumer<Stoppable> consumer = Stoppable::conclude;
      if (mailbox.isPreallocated()) { mailbox.send(actor, Stoppable.class, consumer, null, representationConclude0); }
      else { mailbox.send(new LocalMessage<Stoppable>(actor, Stoppable.class, consumer, representationConclude0)); }
    } else {
      actor.deadLetters().failedDelivery(new DeadLetter(actor, representationConclude0));
    }
  }

  @Override
  public boolean isStopped() {
    return actor.isStopped();
  }

  @Override
  public void stop() {
    if (!actor.isStopped()) {
      final Consumer<ServerRequestResponseChannel> consumer = Stoppable::stop;
      mailbox.send(new LocalMessage<ServerRequestResponseChannel>(actor, ServerRequestResponseChannel.class, consumer, representationStop1));
    } else {
      actor.deadLetters().failedDelivery(new DeadLetter(actor, representationStop1));
    }
  }

  @Override
  public void close() {
    if (!actor.isStopped()) {
      final Consumer<ServerRequestResponseChannel> consumer = ServerRequestResponseChannel::close;
      mailbox.send(new LocalMessage<ServerRequestResponseChannel>(actor, ServerRequestResponseChannel.class, consumer, representationClose2));
    } else {
      actor.deadLetters().failedDelivery(new DeadLetter(actor, representationClose2));
    }
  }

  @Override
  public Completes<Integer> port() {
    if (!actor.isStopped()) {
      final Completes<Integer> completes = Completes.using(actor.scheduler());
      final Consumer<ServerRequestResponseChannel> consumer = ServerRequestResponseChannel::port;
      if (this.mailbox.isPreallocated()) {
        this.mailbox.send(this.actor, ServerRequestResponseChannel.class, consumer, Returns.value(completes), representationPort);
      } else {
        this.mailbox.send(new LocalMessage<ServerRequestResponseChannel>(this.actor, ServerRequestResponseChannel.class, consumer, Returns.value(completes), representationPort));
      }
      return completes;
    } else {
      actor.deadLetters().failedDelivery(new DeadLetter(actor, representationPort));
    }

    return null;
  }
}
