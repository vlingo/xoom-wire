// Copyright © 2012-2020 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.xoom.wire.fdx.outbound;

import io.vlingo.xoom.wire.message.BasicConsumerByteBuffer;
import io.vlingo.xoom.wire.message.RawMessage;
import io.vlingo.xoom.wire.node.Id;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class MockManagedOutboundChannel implements ManagedOutboundChannel {
  public final Id id;
  public final List<String> writes = new ArrayList<>();
  
  public MockManagedOutboundChannel(final Id id) {
    this.id = id;
  }

  @Override
  public void close() {
    writes.clear();
  }

  @Override
  public void write(final ByteBuffer buffer) {
    // make a copy since we are dealing with a read only buffer.
    ByteBuffer copy = BasicConsumerByteBuffer.allocate(0, buffer.capacity())
        .put(buffer)
        .flip()
        .asByteBuffer()
        .order(buffer.order());
    final RawMessage message = RawMessage.readFromWithHeader(copy);
    writes.add(message.asTextMessage());
  }
}
