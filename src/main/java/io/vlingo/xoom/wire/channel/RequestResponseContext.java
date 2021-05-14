// Copyright © 2012-2021 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.xoom.wire.channel;

import io.vlingo.xoom.wire.message.ConsumerByteBuffer;

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

  default void respondWith(final ConsumerByteBuffer buffer) {
    sender().respondWith(this, buffer);
  }

  default void respondWith(final ConsumerByteBuffer buffer, final boolean closeFollowing) {
    sender().respondWith(this, buffer, closeFollowing);
  }

  default void respondWith(final Object response, final boolean closeFollowing) {
    sender().respondWith(this, response, closeFollowing);
  }

  @SuppressWarnings("unchecked")
  default <T> T typed() {
    return (T) this;
  }

  default String remoteAddress() {
    throw new UnsupportedOperationException("Remote address is unavailable by default");
  }
}
