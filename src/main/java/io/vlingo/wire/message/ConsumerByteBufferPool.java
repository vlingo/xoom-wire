package io.vlingo.wire.message;

import io.vlingo.common.pool.ElasticResourcePool;
import io.vlingo.common.pool.ResourceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ConsumerByteBufferPool extends ElasticResourcePool<ConsumerByteBuffer, Void> {

  private static final Logger log = LoggerFactory.getLogger(ConsumerByteBufferPool.class);

  @Override
  public ConsumerByteBuffer acquire() {
    return setPool(super.acquire());
  }

  @Override
  public ConsumerByteBuffer acquire(Void aVoid) {
    return setPool(super.acquire(aVoid));
  }

  private ConsumerByteBuffer setPool(final ConsumerByteBuffer buffer) {
    if (buffer instanceof PoolAwareConsumerByteBuffer) {
      final PoolAwareConsumerByteBuffer pooledBuffer = (PoolAwareConsumerByteBuffer) buffer;
      pooledBuffer.setPool(this);
      pooledBuffer.activate();
    }
    return buffer;
  }

  public ConsumerByteBufferPool(Config config, int maxBufferSize) {
    super(config, new ConsumerByteBufferFactory(maxBufferSize));
  }

  private static final class ConsumerByteBufferFactory implements ResourceFactory<ConsumerByteBuffer, Void> {

    private static final AtomicInteger idSequence = new AtomicInteger(0);

    private final int maxBufferSize;

    private ConsumerByteBufferFactory(int maxBufferSize) {
      this.maxBufferSize = maxBufferSize;
    }

    @Override
    public Class<ConsumerByteBuffer> type() {
      return ConsumerByteBuffer.class;
    }

    @Override
    public ConsumerByteBuffer create(Void aVoid) {
      return new PoolAwareConsumerByteBuffer(
          idSequence.incrementAndGet(), maxBufferSize);
    }

    @Override
    public Void defaultArguments() {
      return null;
    }

    @Override
    public ConsumerByteBuffer reset(ConsumerByteBuffer buffer, Void aVoid) {
      return buffer.clear();
    }

    @Override
    public void destroy(ConsumerByteBuffer buffer) {
    }
  }

  private static final class PoolAwareConsumerByteBuffer extends BasicConsumerByteBuffer {

    private ConsumerByteBufferPool pool;
    private final AtomicBoolean active;

    PoolAwareConsumerByteBuffer(final int id, final int maxBufferSize) {
      super(id, maxBufferSize);
      this.active = new AtomicBoolean(true);
    }

    void setPool(ConsumerByteBufferPool pool) {
      this.pool = pool;
    }

    void activate() {
      active.set(true);
    }

    @Override
    public String toString() {
      return "PooledByteBuffer[id=" + id() + "]";
    }

    @Override
    public void release() {
      if (active.compareAndSet(true, false)) {
        pool.release(this);
      }
      else {
        log.warn("Attempt to release the same buffer more than once");
      }
    }
  }
}
