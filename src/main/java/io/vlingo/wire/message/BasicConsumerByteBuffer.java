package io.vlingo.wire.message;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;

public class BasicConsumerByteBuffer implements ConsumerByteBuffer {
  private final ByteBuffer buffer;
  private final int id;
  private String tag;

  public static BasicConsumerByteBuffer allocate(final int id, final int maxBufferSize) {
    return new BasicConsumerByteBuffer(id, maxBufferSize);
  }

  public BasicConsumerByteBuffer(final int id, final int maxBufferSize) {
    this.id = id;
    this.buffer = ByteBufferAllocator.allocate(maxBufferSize);
  }

  @Override
  public boolean equals(final Object other) {
    if (other == null || other.getClass() != BasicConsumerByteBuffer.class) {
      return false;
    }
    return this.id == ((BasicConsumerByteBuffer) other).id;
  }

  @Override
  public String toString() {
    return "BasicConsumerByteBuffer[id=" + id + "]";
  }

  protected void tag(final String tag) {
    this.tag = tag;
  }


  @Override
  public int id() {
    return id;
  }

  @Override
  public void release() {
  }

  @Override
  public String tag() {
    return tag;
  }

  @Override
  public byte[] array() {
    return buffer.array();
  }

  @Override
  public int arrayOffset() {
    return buffer.arrayOffset();
  }

  @Override
  public boolean hasArray() {
    return buffer.hasArray();
  }

  @Override
  public ByteBuffer asByteBuffer() {
    return buffer;
  }

  @Override
  public CharBuffer asCharBuffer() {
    return buffer.asCharBuffer();
  }

  @Override
  public ShortBuffer asShortBuffer() {
    return buffer.asShortBuffer();
  }

  @Override
  public IntBuffer asIntBuffer() {
    return buffer.asIntBuffer();
  }

  @Override
  public LongBuffer asLongBuffer() {
    return buffer.asLongBuffer();
  }

  @Override
  public FloatBuffer asFloatBuffer() {
    return buffer.asFloatBuffer();
  }

  @Override
  public DoubleBuffer asDoubleBuffer() {
    return buffer.asDoubleBuffer();
  }

  @Override
  public ConsumerByteBuffer compact() {
    buffer.compact();
    return this;
  }

  @Override
  public int capacity() {
    return buffer.capacity();
  }

  @Override
  public int position() {
    return buffer.position();
  }

  @Override
  public ConsumerByteBuffer position(int newPosition) {
    buffer.position(newPosition);
    return this;
  }

  @Override
  public int limit() {
    return buffer.limit();
  }

  @Override
  public ConsumerByteBuffer limit(int newLimit) {
    buffer.limit(newLimit);
    return this;
  }

  @Override
  public ConsumerByteBuffer mark() {
    buffer.mark();
    return this;
  }

  @Override
  public ConsumerByteBuffer reset() {
    buffer.reset();
    return this;
  }

  @Override
  public ConsumerByteBuffer clear() {
    buffer.clear();
    return this;
  }

  @Override
  public ConsumerByteBuffer flip() {
    buffer.flip();
    return this;
  }

  @Override
  public ConsumerByteBuffer rewind() {
    buffer.rewind();
    return this;
  }

  @Override
  public int remaining() {
    return buffer.remaining();
  }

  @Override
  public boolean hasRemaining() {
    return buffer.hasRemaining();
  }

  @Override
  public boolean isReadOnly() {
    return buffer.isReadOnly();
  }

  @Override
  public boolean isDirect() {
    return buffer.isDirect();
  }

  @Override
  public byte get() {
    return buffer.get();
  }

  @Override
  public byte get(int index) {
    return buffer.get(index);
  }

  @Override
  public ByteBuffer get(byte[] destination) {
    return buffer.get(destination);
  }

  @Override
  public ByteBuffer get(byte[] destination, int offset, int length) {
    return buffer.get(destination, offset, length);
  }

  @Override
  public char getChar() {
    return buffer.getChar();
  }

  @Override
  public char getChar(int index) {
    return buffer.getChar(index);
  }

  @Override
  public short getShort() {
    return buffer.getShort();
  }

  @Override
  public short getShort(int index) {
    return buffer.getShort(index);
  }

  @Override
  public int getInt() {
    return buffer.getInt();
  }

  @Override
  public int getInt(int index) {
    return buffer.getInt(index);
  }

  @Override
  public long getLong() {
    return buffer.getLong();
  }

  @Override
  public long getLong(int index) {
    return buffer.getLong(index);
  }

  @Override
  public float getFloat() {
    return buffer.getFloat();
  }

  @Override
  public float getFloat(int index) {
    return buffer.getFloat(index);
  }

  @Override
  public double getDouble() {
    return buffer.getDouble();
  }

  @Override
  public double getDouble(int index) {
    return buffer.getDouble(index);
  }

  @Override
  public ConsumerByteBuffer put(final ByteBuffer source) {
    buffer.put(source);
    return this;
  }

  @Override
  public ConsumerByteBuffer put(byte b) {
    buffer.put(b);
    return this;
  }

  @Override
  public ConsumerByteBuffer put(int index, byte b) {
    buffer.put(index, b);
    return this;
  }

  @Override
  public ConsumerByteBuffer put(byte[] src, int offset, int length) {
    buffer.put(src, offset, length);
    return this;
  }

  @Override
  public ConsumerByteBuffer put(byte[] src) {
    buffer.put(src);
    return this;
  }

  @Override
  public ConsumerByteBuffer putChar(char value) {
    buffer.putChar(value);
    return this;
  }

  @Override
  public ConsumerByteBuffer putChar(int index, char value) {
    buffer.putChar(index, value);
    return this;
  }

  @Override
  public ConsumerByteBuffer putShort(short value) {
    buffer.putShort(value);
    return this;
  }

  @Override
  public ConsumerByteBuffer putShort(int index, short value) {
    buffer.putShort(index, value);
    return this;
  }

  @Override
  public ConsumerByteBuffer putInt(int value) {
    buffer.putInt(value);
    return this;
  }

  @Override
  public ConsumerByteBuffer putInt(int index, int value) {
    buffer.putInt(index, value);
    return this;
  }

  @Override
  public ConsumerByteBuffer putLong(long value) {
    buffer.putLong(value);
    return this;
  }

  @Override
  public ConsumerByteBuffer putLong(int index, long value) {
    buffer.putLong(index, value);
    return this;
  }

  @Override
  public ConsumerByteBuffer putFloat(float value) {
    buffer.putFloat(value);
    return this;
  }

  @Override
  public ConsumerByteBuffer putFloat(int index, float value) {
    buffer.putFloat(index, value);
    return this;
  }

  @Override
  public ConsumerByteBuffer putDouble(double value) {
    buffer.putDouble(value);
    return this;
  }

  @Override
  public ConsumerByteBuffer putDouble(int index, double value) {
    buffer.putDouble(index, value);
    return this;
  }
}
