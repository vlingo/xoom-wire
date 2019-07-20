// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.
package io.vlingo.wire.fdx.outbound.rsocket;

import io.rsocket.RSocket;
import io.rsocket.RSocketFactory;
import io.rsocket.transport.netty.client.TcpClientTransport;
import io.rsocket.util.ByteBufPayload;
import io.vlingo.actors.Logger;
import io.vlingo.wire.fdx.outbound.ManagedOutboundChannel;
import io.vlingo.wire.node.Address;
import io.vlingo.wire.node.Node;

import java.nio.ByteBuffer;

public class RSocketOutboundChannel implements ManagedOutboundChannel {
  private final Node node;
  private final Logger logger;
  private final RSocketFactory.Start<RSocket> socketFactory;
  private RSocket socket;

  public RSocketOutboundChannel(final Node node, final Address address, final Logger logger) {
    this.node = node;
    this.logger = logger;

    this.socketFactory = RSocketFactory
            .connect()
            .transport(TcpClientTransport.create(address.hostName(), address.port()));
  }

  @Override
  public void close() {
    if (this.socket != null && !this.socket.isDisposed()){
      this.socket.dispose();
    }
  }

  @Override
  public void write(final ByteBuffer buffer) {
    prepareSocket();
    socket.fireAndForget(ByteBufPayload.create(buffer))
          .doOnError(throwable -> {
            logger.error("Failed write to node {}, because: {}", node, throwable.getMessage(), throwable);
          })
          .subscribe();
  }

  private void prepareSocket() {
    if (this.socket == null){
      this.socket = socketFactory.start().block();
    }
  }
}
