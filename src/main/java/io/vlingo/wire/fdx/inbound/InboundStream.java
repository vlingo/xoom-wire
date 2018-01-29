// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.fdx.inbound;

import io.vlingo.actors.Definition;
import io.vlingo.actors.Stage;
import io.vlingo.actors.Startable;
import io.vlingo.actors.Stoppable;
import io.vlingo.wire.channel.ChannelReader;
import io.vlingo.wire.node.AddressType;

public interface InboundStream extends Startable, Stoppable {
  public static InboundStream instance(
          final Stage stage,
          final InboundStreamInterest interest,
          final int port,
          final AddressType addressType,
          final String inboundName,
          final int maxMessageSize,
          final long probeInterval,
          final long probeTimeout)
  throws Exception {
    
    final ChannelReader reader =
            new SocketChannelInboundReader(port, inboundName, maxMessageSize, probeTimeout, stage.world().defaultLogger());
    
    final Definition definition =
            Definition.has(
                    InboundStreamActor.class,
                    Definition.parameters(interest, addressType, reader, probeInterval),
                    inboundName + "-inbound");
    
    final InboundStream inboundStream = stage.actorFor(definition, InboundStream.class);
    
    return inboundStream;
  }
}
