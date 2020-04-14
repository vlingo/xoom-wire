// Copyright © 2012-2020 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.channel;

import io.vlingo.wire.message.ConsumerByteBuffer;

public interface RequestResponseContext<R> {
  <T> T consumerData();
  <T> T consumerData(final T data);
  boolean hasConsumerData();
  String id();
  ResponseSenderChannel sender();
  void whenClosing(final Object data);

  default void abandon() {
    sender().abandon(this);
  }

  default void explicitClose(final boolean option) {
    sender().explicitClose(this, option);
  }

  default void respondWith(final ConsumerByteBuffer buffer) {
    sender().respondWith(this, buffer);
  }
}
