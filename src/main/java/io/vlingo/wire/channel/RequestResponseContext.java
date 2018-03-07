// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.channel;

import io.vlingo.wire.message.ConsumerByteBuffer;

public interface RequestResponseContext<R> {
  R reference();
  ConsumerByteBuffer requestBuffer();
  ResponseData responseData();
  ResponseSenderChannel sender();

  default void abandon() {
    sender().abandon(this);
  }

  default void respondOnceWith(final byte[] bytes) {
    final ResponseData responseData = responseData();
    responseData.buffer.put(bytes).flip();
    sender().respondOnceWith(this);
  }

  default void respondWith(final byte[] bytes, final boolean completes) {
    final ResponseData responseData = responseData();
    responseData.buffer.put(bytes).flip();
    sender().respondWith(this, completes);
  }
}
