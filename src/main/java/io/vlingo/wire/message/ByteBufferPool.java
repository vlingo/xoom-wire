// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.message;

import java.util.concurrent.atomic.AtomicBoolean;

public class ByteBufferPool {
  private final PooledByteBuffer[] pool;
  private final int poolSize;

  public ByteBufferPool(final int poolSize, final int maxBufferSize) {
    this.poolSize = poolSize;
    this.pool = new PooledByteBuffer[poolSize];

    for (int idx = 0; idx < poolSize; ++idx) {
      pool[idx] = new PooledByteBuffer(idx, maxBufferSize);
    }
  }

  public int available() {
    // this is not an accurate calculation because the number
    // of in-use buffers could change before the loop completes
    // and/or the result is answered.
    
    int available = poolSize;
    
    for (int idx = 0; idx < poolSize; ++idx) {
      if (pool[idx].isInUse()) {
        --available;
      }
    }
    
    return available;
  }

  public PooledByteBuffer access() {
    return accessFor("untagged");
  }

  public PooledByteBuffer accessFor(final String tag) {
    return accessFor(tag, Integer.MAX_VALUE);
  }

  public PooledByteBuffer accessFor(final String tag, final int retries) {
    while (true) {
      for (int idx = 0; idx < poolSize; ++idx) {
        final PooledByteBuffer buffer = pool[idx];
        if (buffer.claimUse(tag)) {
          return buffer;
        }
      }
    }
  }

  public int size() {
    return pool.length;
  }

  public class PooledByteBuffer extends BasicConsumerByteBuffer {
    private final AtomicBoolean inUse;

    PooledByteBuffer(final int id, final int maxBufferSize) {
      super(id, maxBufferSize);

      this.inUse = new AtomicBoolean(false);
    }

    @Override
    public boolean equals(final Object other) {
      if (other == null || other.getClass() != BasicConsumerByteBuffer.class) {
        return false;
      }
      return this.id() == ((BasicConsumerByteBuffer) other).id();
    }
  
    @Override
    public String toString() {
      return "PooledByteBuffer[id=" + id() + "]";
    }

    @Override
    public void release() {
      if (!inUse.get()) {
        throw new IllegalStateException("Attempt to release unclaimed buffer: " + this);
      }
      notInUse();
    }

    private boolean claimUse(final String tag) {
      if (inUse.compareAndSet(false, true)) {
        tag(tag);
        asByteBuffer().clear();
        return true;
      }
      return false;
    }

    private void notInUse() {
      inUse.set(false);
    }

    private boolean isInUse() {
      return inUse.get();
    }
  }
}
