// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.channel;

import io.vlingo.wire.message.ConsumerByteBuffer;

public final class ResponseData {
  enum Stage { New, Modified, Sent };
  
  public final ConsumerByteBuffer buffer;
  private volatile Stage stage;
  
  public ResponseData(final ConsumerByteBuffer buffer) {
    this.buffer = buffer;
    this.stage = Stage.New;
  }

  public int id() {
    return buffer.id();
  }

  public boolean isModified() {
    return stage == Stage.Modified;
  }

  public void modified() {
    stage = Stage.Modified;
  }

  public boolean wasSent() {
    return stage == Stage.Sent;
  }

  public void sent() {
    stage = Stage.Sent;
  }

  @Override
  public boolean equals(final Object other) {
    if (this != other || other == null || other.getClass() != ResponseData.class) {
      return false;
    }
    return this.id() == ((ResponseData) other).id();
  }
}
