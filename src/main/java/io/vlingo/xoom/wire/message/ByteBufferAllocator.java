// Copyright © 2012-2022 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.xoom.wire.message;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ByteBufferAllocator {
  private static final boolean BigEndian = ByteOrder.nativeOrder().equals(ByteOrder.BIG_ENDIAN);
  
  public static ByteBuffer allocate(final int capacity) {
    final ByteBuffer buffer = ByteBuffer.allocate(capacity);
    buffer.order(BigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
    return buffer;
  }
  
  public static ByteBuffer allocateDirect(final int capacity) {
    final ByteBuffer buffer = ByteBuffer.allocateDirect(capacity);
    buffer.order(BigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
    return buffer;
  }
}
