// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.multicast;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

import io.vlingo.actors.Logger;
import io.vlingo.wire.channel.ChannelMessageDispatcher;
import io.vlingo.wire.channel.ChannelPublisher;
import io.vlingo.wire.channel.ChannelReaderConsumer;
import io.vlingo.wire.channel.SocketChannelSelectionReader;
import io.vlingo.wire.message.ByteBufferAllocator;
import io.vlingo.wire.message.PublisherAvailability;
import io.vlingo.wire.message.RawMessage;
import io.vlingo.wire.message.RawMessageBuilder;

public class MulticastPublisherReader implements ChannelPublisher, ChannelMessageDispatcher {
  private final RawMessage availability;
  private final DatagramChannel channel;
  private boolean closed;
  private final ChannelReaderConsumer consumer;
  private final InetSocketAddress groupAddress;
  private final Logger logger;
  private final ByteBuffer messageBuffer;
  private final Queue<RawMessage> messageQueue;
  private final String name;
  private final long processTimeout;
  private final InetSocketAddress publisherAddress;
  private final Selector selector;
  
  public MulticastPublisherReader(
          final String name,
          final Group group,
          final int maxMessageSize,
          final long processTimeout,
          final ChannelReaderConsumer consumer,
          final Logger logger)
  throws Exception {
    
    this.name = name;
    this.groupAddress = new InetSocketAddress(InetAddress.getByName(group.address()), group.port());
    this.processTimeout = processTimeout;
    this.consumer = consumer;
    this.logger = logger;
    this.messageBuffer = ByteBufferAllocator.allocate(maxMessageSize);
    this.messageQueue = new LinkedList<>();
    this.channel = DatagramChannel.open();
    this.selector = Selector.open();

    // binds to an assigned local address that is
    // published as my availabilityMessage
    channel.bind(null);
    
    channel.configureBlocking(false);
    this.channel.register(selector, SelectionKey.OP_WRITE | SelectionKey.OP_READ, new RawMessageBuilder(maxMessageSize));
    
    this.publisherAddress = (InetSocketAddress) channel.getLocalAddress();
    this.availability = availabilityMessage();
  }

  //====================================
  // ChannelPublisher
  //====================================

  @Override
  public void close() {
    if (closed) return;
    
    closed = true;
    
    try {
      selector.close();
    } catch (Exception e) {
      logger.log("Failed to close multicast publisher selctor for: '" + name + "'", e);
    }
    
    try {
      channel.close();
    } catch (Exception e) {
      logger.log("Failed to close multicast publisher channel for: '" + name + "'", e);
    }
  }
  
  @Override
  public void processChannel() {
    if (closed) return;
    
    try {
      if (selector.select(processTimeout) > 0) {
        final Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();

        while (iterator.hasNext()) {
          final SelectionKey key = iterator.next();
          iterator.remove();

          if (key.isValid()) {
            if (key.isWritable()) {
              sendMax();
            } else if (key.isReadable()) {
              receive(key);
            }
          }
        }
      }
    } catch (IOException e) {
      logger.log("Failed to read channel selector for: '" + name + "'", e);
    }
  }

  @Override
  public void sendAvailability() {
    send(availability);
  }

  @Override
  public void send(final RawMessage message) {
    final int length = message.length();
    
    if (length <= 0) {
      throw new IllegalArgumentException("The message length must be greater than zero.");
    }

    if (length > messageBuffer.capacity()) {
      throw new IllegalArgumentException("The message length is greater than " + messageBuffer.capacity());
    }

    messageQueue.add(message);
  }

  //====================================
  // ChannelMessageDispatcher
  //====================================

  @Override
  public ChannelReaderConsumer consumer() {
    return consumer;
  }

  @Override
  public Logger logger() {
    return logger;
  }

  @Override
  public String name() {
    return name;
  }

  //====================================
  // internal implementation
  //====================================

  private RawMessage availabilityMessage() {
    final String message =
            new PublisherAvailability(
                    name,
                    publisherAddress.getHostName(),
                    publisherAddress.getPort())
            .toString();
    
    final ByteBuffer buffer = ByteBufferAllocator.allocate(message.length());
    buffer.put(message.getBytes());
    buffer.flip();
    
    return RawMessage.readFromWithoutHeader(buffer);
  }

  private void receive(final SelectionKey key) throws IOException {
    new SocketChannelSelectionReader(this, key).read();
  }

  private void sendMax() throws IOException {
    while (true) {
      final RawMessage message = messageQueue.peek();
      
      if (message == null) {
        return;
      } else {
        messageBuffer.clear();
        message.copyBytesTo(messageBuffer);
        messageBuffer.flip();
        final int bytes = channel.send(messageBuffer, groupAddress);
        if (bytes > 0) {
          messageQueue.remove();
        } else {
          return;
        }
      }
    }
  }
}
