// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.channel;

import io.vlingo.wire.message.ConsumerByteBuffer;

public final class ResponseData {
  public final ConsumerByteBuffer buffer;
  
  public ResponseData(final ConsumerByteBuffer buffer) {
    this.buffer = buffer;
  }

  public int id() {
    return buffer.id();
  }

  @Override
  public boolean equals(final Object other) {
    if (this != other || other == null || other.getClass() != ResponseData.class) {
      return false;
    }
    return this.id() == ((ResponseData) other).id();
  }
}
