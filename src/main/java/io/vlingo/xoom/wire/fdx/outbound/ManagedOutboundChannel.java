// Copyright © 2012-2022 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.xoom.wire.fdx.outbound;

import io.vlingo.xoom.common.Completes;

import java.nio.ByteBuffer;
import java.util.function.Supplier;

public interface ManagedOutboundChannel {
  void close();
  void write(final ByteBuffer buffer);
  default Completes<Void> writeAsync(final ByteBuffer buffer) {
    Supplier<Void> sup = () -> {
      write(buffer);
      return null;
    };
    return Completes.withSuccess(sup.get());
  }
}
