package io.vlingo.wire.message;

import io.vlingo.common.pool.ElasticResourcePool;
import io.vlingo.common.pool.ResourceFactory;

import java.util.concurrent.atomic.AtomicInteger;

public class ConsumerByteBufferPool extends ElasticResourcePool<ConsumerByteBuffer, Void> {

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
      ((PoolAwareConsumerByteBuffer) buffer).setPool(this);
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

    PoolAwareConsumerByteBuffer(final int id, final int maxBufferSize) {
      super(id, maxBufferSize);
    }

    public void setPool(ConsumerByteBufferPool pool) {
      this.pool = pool;
    }

    @Override
    public String toString() {
      return "PooledByteBuffer[id=" + id() + "]";
    }

    @Override
    public void release() {
      pool.release(this);
    }
  }
}
