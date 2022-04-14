// Copyright © 2012-2022 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.xoom.wire.fdx.inbound;

import io.vlingo.xoom.actors.ActorInstantiator;
import io.vlingo.xoom.actors.Definition;
import io.vlingo.xoom.actors.Stage;
import io.vlingo.xoom.actors.Startable;
import io.vlingo.xoom.actors.Stoppable;
import io.vlingo.xoom.wire.channel.ChannelReader;
import io.vlingo.xoom.wire.node.AddressType;

public interface InboundStream extends Startable, Stoppable {
  public static InboundStream instance(
          final Stage stage,
          final InboundChannelReaderProvider channelReaderProvider,
          final InboundStreamInterest interest,
          final int port,
          final AddressType addressType,
          final String inboundName,
          final long probeInterval)
  throws Exception {

    final ChannelReader channelReader = channelReaderProvider.channelFor(port, inboundName);

    final Definition definition =
            Definition.has(
                    InboundStreamActor.class,
                    new InboundStreamInstantiator(interest, addressType, channelReader, probeInterval),
                    inboundName + "-inbound");

    return stage.actorFor(InboundStream.class, definition);
  }

  static class InboundStreamInstantiator implements ActorInstantiator<InboundStreamActor> {
    private static final long serialVersionUID = -6324545387015266770L;

    private final AddressType addressType;
    private final ChannelReader channelReader;
    private final InboundStreamInterest interest;
    private final long probeInterval;

    public InboundStreamInstantiator(
            final InboundStreamInterest interest,
            final AddressType addressType,
            final ChannelReader channelReader,
            final long probeInterval) {
      this.interest = interest;
      this.addressType = addressType;
      this.channelReader = channelReader;
      this.probeInterval = probeInterval;
    }

    @Override
    public InboundStreamActor instantiate() {
      return new InboundStreamActor(interest, addressType, channelReader, probeInterval);
    }

    @Override
    public Class<InboundStreamActor> type() {
      return InboundStreamActor.class;
    }
  }
}
