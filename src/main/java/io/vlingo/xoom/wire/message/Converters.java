// Copyright © 2012-2020 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.xoom.wire.message;

import static java.lang.Character.MAX_SURROGATE;
import static java.lang.Character.MIN_SURROGATE;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class Converters {
  private static Charset CHARSET_VALUE = Charset.forName(StandardCharsets.UTF_8.name());

  public static String bytesToText(final byte[] bytes, final int index, final int length) {
    return new String(bytes, index, length, CHARSET_VALUE);
  }

  public static void changeCharset(final String charsetName) {
    CHARSET_VALUE = Charset.forName(charsetName);
  }

  public static byte[] textToBytes(final String text) {
    return text.getBytes(CHARSET_VALUE);
  }

  /** Uses buffer.flip() and then buffer.clear()
   * @param sendingNodeId short
   * @param buffer ByteBuffer
   * @return RawMessage
   */
  public static RawMessage toRawMessage(final short sendingNodeId, final ByteBuffer buffer) {
    buffer.flip();

    final RawMessage message = new RawMessage(buffer.limit());

    message.put(buffer, false);

    buffer.clear();

    final RawMessageHeader header = new RawMessageHeader(sendingNodeId, (short) 0, (short) message.length());

    message.header(header);

    return message;
  }

  /*
   * Copyright (C) 2013 The Guava Authors
   *
   * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
   * in compliance with the License. You may obtain a copy of the License at
   *
   * http://www.apache.org/licenses/LICENSE-2.0
   *
   * Unless required by applicable law or agreed to in writing, software distributed under the License
   * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
   * or implied. See the License for the specific language governing permissions and limitations under
   * the License.
   */
  /**
   * Returns the number of bytes in the UTF-8-encoded form of {@code sequence}. For a string, this
   * method is equivalent to {@code string.getBytes(UTF_8).length}, but is more efficient in both
   * time and space.
   *
   * @throws IllegalArgumentException if {@code sequence} contains ill-formed UTF-16 (unpaired
   *     surrogates)
   */
  public static int encodedLength(CharSequence sequence) {
    // Warning to maintainers: this implementation is highly optimized.
    int utf16Length = sequence.length();
    int utf8Length = utf16Length;
    int i = 0;

    // This loop optimizes for pure ASCII.
    while (i < utf16Length && sequence.charAt(i) < 0x80) {
      i++;
    }

    // This loop optimizes for chars less than 0x800.
    for (; i < utf16Length; i++) {
      char c = sequence.charAt(i);
      if (c < 0x800) {
        utf8Length += ((0x7f - c) >>> 31); // branch free!
      } else {
        utf8Length += encodedLengthGeneral(sequence, i);
        break;
      }
    }

    if (utf8Length < utf16Length) {
      // Necessary and sufficient condition for overflow because of maximum 3x expansion
      throw new IllegalArgumentException(
          "UTF-8 length does not fit in int: " + (utf8Length + (1L << 32)));
    }
    return utf8Length;
  }

  private static int encodedLengthGeneral(CharSequence sequence, int start) {
    int utf16Length = sequence.length();
    int utf8Length = 0;
    for (int i = start; i < utf16Length; i++) {
      char c = sequence.charAt(i);
      if (c < 0x800) {
        utf8Length += (0x7f - c) >>> 31; // branch free!
      } else {
        utf8Length += 2;
        // jdk7+: if (Character.isSurrogate(c)) {
        if (MIN_SURROGATE <= c && c <= MAX_SURROGATE) {
          // Check that we have a well-formed surrogate pair.
          if (Character.codePointAt(sequence, i) == c) {
            throw new IllegalArgumentException(unpairedSurrogateMsg(i));
          }
          i++;
        }
      }
    }
    return utf8Length;
  }

  private static String unpairedSurrogateMsg(int i) {
    return "Unpaired surrogate at index " + i;
  }
}
