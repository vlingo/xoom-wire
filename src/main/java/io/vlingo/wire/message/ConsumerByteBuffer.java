// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.message;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;

public interface ConsumerByteBuffer {
  int id();
  
  void release();
  
  byte[] array();
  int arrayOffset();
  boolean hasArray();
  
  ByteBuffer asByteBuffer();
  CharBuffer asCharBuffer();
  ShortBuffer asShortBuffer();
  IntBuffer asIntBuffer();
  LongBuffer asLongBuffer();
  FloatBuffer asFloatBuffer();
  DoubleBuffer asDoubleBuffer();

  ConsumerByteBuffer compact();
  
  int capacity();
  
  int position();
  ConsumerByteBuffer position(int newPosition);
  
  int limit();
  ConsumerByteBuffer limit(int newLimit);
  
  ConsumerByteBuffer mark();
  ConsumerByteBuffer reset();
  
  ConsumerByteBuffer clear();
  
  ConsumerByteBuffer flip();
  
  ConsumerByteBuffer rewind();
  
  int remaining();
  boolean hasRemaining();
  
  boolean isReadOnly();
  boolean isDirect();
  
  byte get();
  byte get(final int index);
  ByteBuffer get(final byte[] destination);
  ByteBuffer get(final byte[] destination, final int offset, final int length);
  char getChar();
  char getChar(int index);
  short getShort();
  short getShort(int index);
  int getInt();
  int getInt(int index);
  long getLong();
  long getLong(int index);
  float getFloat();
  float getFloat(int index);
  double getDouble();
  double getDouble(int index);
  
  ConsumerByteBuffer put(final ByteBuffer soruce);
  ConsumerByteBuffer put(final byte b);
  ConsumerByteBuffer put(final int index, final byte b);
  ConsumerByteBuffer put(byte[] src, int offset, int length);
  ConsumerByteBuffer put(byte[] src);
  ConsumerByteBuffer putChar(char value);
  ConsumerByteBuffer putChar(int index, char value);
  ConsumerByteBuffer putShort(short value);
  ConsumerByteBuffer putShort(int index, short value);
  ConsumerByteBuffer putInt(int value);
  ConsumerByteBuffer putInt(int index, int value);
  ConsumerByteBuffer putLong(long value);
  ConsumerByteBuffer putLong(int index, long value);
  ConsumerByteBuffer putFloat(float value);
  ConsumerByteBuffer putFloat(int index, float value);
  ConsumerByteBuffer putDouble(double value);
  ConsumerByteBuffer putDouble(int index, double value);
}

