// Copyright © 2012-2020 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.fdx.outbound;

import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;

public interface ManagedOutboundChannel {
  void close();
  void write(final ByteBuffer buffer);
  default Mono<Void> writeAsync(final ByteBuffer buffer) {
    return Mono.fromRunnable(() -> write(buffer));
  }
}
