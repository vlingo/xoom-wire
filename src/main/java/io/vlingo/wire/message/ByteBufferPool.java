// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.message;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class ByteBufferPool {
  private final List<PooledByteBuffer> pool;
  private final int poolSize;
  
  public ByteBufferPool(final int poolSize, final int maxBufferSize) {
    this.poolSize = poolSize;
    this.pool = new ArrayList<PooledByteBuffer>(poolSize);
    
    for (int idx = 0; idx < poolSize; ++idx) {
      pool.add(new PooledByteBuffer(maxBufferSize));
    }
  }
  
  public int available() {
    // this is not an accurate calculation because the number
    // of in-use buffers could change before the loop completes
    // and/or the result is answered.
    
    int available = poolSize;
    
    for (PooledByteBuffer buffer : pool) {
      if (buffer.isInUse()) {
        --available;
      }
    }
    
    return available;
  }
  
  public PooledByteBuffer access() {
    while (true) {
      for (int idx = 0; idx < poolSize; ++idx) {
        final PooledByteBuffer buffer = pool.get(idx);
        if (buffer.claimUse()) {
          return buffer;
        }
      }
    }
  }
  
  public class PooledByteBuffer {
    private final ByteBuffer buffer;
    private final AtomicBoolean inUse;
    
    PooledByteBuffer(final int maxBufferSize) {
      this.buffer = ByteBufferAllocator.allocate(maxBufferSize);
      this.inUse = new AtomicBoolean(false);
    }
    
    public ByteBuffer buffer() {
      return buffer;
    }
    
    public ByteBuffer flip() {
      buffer.flip();
      
      return buffer;
    }
    
    public int limit() {
      return buffer.limit();
    }
    
    public ByteBuffer put(final ByteBuffer source) {
      return buffer.put(source);
    }
    
    public void release() {
      notInUse();
    }
    
    private boolean claimUse() {
      if (inUse.compareAndSet(false, true)) {
        buffer.clear();
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
