// Copyright © 2012-2020 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.channel;


import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

import io.vlingo.actors.Actor;
import io.vlingo.actors.Stoppable;
import io.vlingo.common.Cancellable;
import io.vlingo.common.Scheduled;
import io.vlingo.common.pool.ResourcePool;
import io.vlingo.wire.message.BasicConsumerByteBuffer;
import io.vlingo.wire.message.ConsumerByteBuffer;
import io.vlingo.wire.message.Converters;

public class SocketChannelSelectionProcessorActor extends Actor
    implements SocketChannelSelectionProcessor, ResponseSenderChannel, Scheduled<Object>, Stoppable {

  private final Cancellable cancellable;
  private int contextId;
  private final String name;
  private final long probeTimeout;
  private final RequestChannelConsumerProvider provider;
  private final ResourcePool<ConsumerByteBuffer, String> requestBufferPool;
  private final ResponseSenderChannel responder;
  private final RefreshableSelector selector;
  private final LinkedList<Context> writableContexts;

  @SuppressWarnings("unchecked")
  public SocketChannelSelectionProcessorActor(
          final RequestChannelConsumerProvider provider,
          final String name,
          final ResourcePool<ConsumerByteBuffer, String> requestBufferPool,
          final long probeInterval,
          final long probeTimeout) {
    this.logger().debug("Probe interval: " + probeInterval + " Probe timeout: " + probeTimeout);
    this.provider = provider;
    this.name = name;
    this.requestBufferPool = requestBufferPool;
    this.probeTimeout = probeTimeout;
    this.selector = RefreshableSelector.open(name);
    this.responder = selfAs(ResponseSenderChannel.class);
    this.writableContexts = new LinkedList<>();

    this.cancellable = stage().scheduler().schedule(selfAs(Scheduled.class), null, 100, probeInterval);
  }


  //=========================================
  // ResponseSenderChannel
  //=========================================

  @Override
  public void abandon(final RequestResponseContext<?> context) {
    ((Context) context).close();
  }

  @Override
  public void close() {
    if (isStopped()) return;

    selfAs(Stoppable.class).stop();
  }

  @Override
  public void respondWith(final RequestResponseContext<?> context, final ConsumerByteBuffer buffer) {
    respondWith(context, buffer, false);
  }

  @Override
  public void respondWith(final RequestResponseContext<?> context, final ConsumerByteBuffer buffer, final boolean closeFollowing) {
    final Context internalContext = (Context) context;
    internalContext.queueWritable(buffer);
    internalContext.requireExplicitClose(!closeFollowing);
  }

  @Override
  public void respondWith(final RequestResponseContext<?> context, final Object response, final boolean closeFollowing) {
    final String textResponse = response.toString();

    final ConsumerByteBuffer buffer =
            new BasicConsumerByteBuffer(0, textResponse.length() + 1024)
            .put(Converters.textToBytes(textResponse)).flip();

    respondWith(context, buffer, closeFollowing);
  }


  //=========================================
  // SocketChannelSelectionProcessor
  //=========================================

  @Override
  public void process(final SocketChannel clientChannel) {
    try {
      selector.registerWith(clientChannel, SelectionKey.OP_READ, new Context(clientChannel));
    } catch (Exception e) {
      final String message = "Failed to accept client socket for " + name + " because: " + e.getMessage();
      logger().error(message, e);
      throw new IllegalArgumentException(message);
    }
  }


  //=========================================
  // Scheduled
  //=========================================

  @Override
  public void intervalSignal(final Scheduled<Object> scheduled, final Object data) {
    probeChannel();
  }


  //=========================================
  // Stoppable
  //=========================================

  @Override
  public void stop() {
    cancellable.cancel();

    try {
      selector.close();
    } catch (Exception e) {
      logger().error("Failed to close selector for " + name + " while stopping because: " + e.getMessage(), e);
    }
  }


  //=========================================
  // internal implementation
  //=========================================

  private void closeForPeerDisconnect(final Context context, final SelectionKey key) {
    try {
      context.close();
    } catch (Exception e) {
      // already cancelled/closed; ignore
    }
    try {
      key.cancel();
    } catch (Exception e) {
      // already cancelled/closed; ignore
    }
  }

  private void probeChannel() {
    if (isStopped()) return;

    try {
      final Iterator<SelectionKey> iterator = selector.select(probeTimeout);

      while (iterator.hasNext()) {
        final SelectionKey key = iterator.next();
        iterator.remove();

        if (key.isValid()) {
          if (key.isReadable()) {
            read(key);
          } else if (key.isWritable()) {
            write(key);
          }
        }
      }

      while (!writableContexts.isEmpty()) {
        write(writableContexts.poll());
      }
    } catch (ClosedSelectorException e) {
      logger().error("Failed client channel processing for " + name + " because selector is closed.");
    } catch (Exception e) {
      logger().error("Failed client channel processing for " + name + " because: " + e.getMessage(), e);
    }
  }

  private void read(final SelectionKey key) throws IOException {
    final SocketChannel channel = (SocketChannel) key.channel();

    if (!channel.isOpen()) {
      key.cancel();
      return;
    }

    final Context context = (Context) key.attachment();
    final ConsumerByteBuffer buffer = context.requestBuffer().clear();
    final ByteBuffer readBuffer = buffer.asByteBuffer();

    int totalBytesRead = 0;
    int bytesRead = 0;

    try {
      do {
        bytesRead = channel.read(readBuffer);
        totalBytesRead += bytesRead;
      } while (bytesRead > 0);
    } catch (Exception e) {
      // likely a forcible close by the client,
      // so force close and cleanup
      bytesRead = -1;
    }

    if (bytesRead == -1) {
      closeForPeerDisconnect(context, key);
    }

    if (totalBytesRead > 0) {
      context.consumer().consume(context, buffer.flip());
    } else {
      context.close();
    }
  }

  private void write(final SelectionKey key) throws Exception {
    write((Context) key.attachment());
  }

  private void write(final Context context) throws Exception {
    if (context.isChannelClosed()) {
      context.close();
      return;
    }
    if (!context.writeMode) {
      if (context.hasNextWritable()) {
        writeWithCachedData(context, context.clientChannel);
      }
    }
    context.eagerClose();
  }

  private void writeWithCachedData(final Context context, final SocketChannel channel) throws Exception {
    for (ConsumerByteBuffer buffer = context.nextWritable() ; buffer != null; buffer = context.nextWritable()) {
      writeWithCachedData(context, channel, buffer);
    }
  }

  private void writeWithCachedData(final Context context, final SocketChannel clientChannel, ConsumerByteBuffer buffer) throws Exception {
    try {
      final ByteBuffer responseBuffer = buffer.asByteBuffer();

      while (responseBuffer.hasRemaining()) {
        if (clientChannel.write(responseBuffer) < 1) {
          context.setWriteMode(true);
          return;
        }
      }
      context.confirmCurrentWritable(buffer);
    } catch (Exception e) {
      logger().error("Failed to write buffer for " + name + " with channel " + clientChannel.getRemoteAddress() + " because: " + e.getMessage(), e);
    }
  }


  //=========================================
  // internal implementation
  //=========================================

  private class Context implements RequestResponseContext<SocketChannel> {
    private final SocketChannel clientChannel;
    private Object closingData;
    private final RequestChannelConsumer consumer;
    private Object consumerData;
    private final String id;
    private boolean requireExplicitClose;
    private final Queue<ConsumerByteBuffer> writables;
    private boolean writeMode;

    @Override
    @SuppressWarnings("unchecked")
    public <T> T consumerData() {
      return (T) consumerData;
    }

    @Override
    public <T> T consumerData(final T workingData) {
      this.consumerData = workingData;
      return workingData;
    }

    @Override
    public boolean hasConsumerData() {
      return consumerData != null;
    }

    @Override
    public String id() {
      return id;
    }

    @Override
    public ResponseSenderChannel sender() {
      return responder;
    }

    @Override
    public void whenClosing(final Object data) {
      this.closingData = data;
    }

    Context(final SocketChannel clientChannel) {
      this.clientChannel = clientChannel;
      this.consumer = provider.requestChannelConsumer();
      this.id = "" + (++contextId);
      this.requireExplicitClose = true;
      this.writables = new LinkedList<>();
      this.writeMode = false;
    }

    boolean isChannelClosed() {
      return !clientChannel.isOpen();
    }

    boolean isChannelOpen() {
      return clientChannel.isOpen();
    }

    void close() {
      try {
        consumer().closeWith(this, closingData);
        whenClosing(null);
        selector.keyFor(clientChannel).cancel();
        clientChannel.close();
      } catch (Exception e) {
        if (hasNextWritable()) {
          logger().info("Client channel didn't close normally and still has writable data.");
        }
      }
    }

    void eagerClose() {
      if (requireExplicitClose) return;

      if (isChannelOpen()) {
//        logger().debug("############ EAGER CLOSE: NOT KEEP ALIVE ############");
        close();
//      } else {
//        if (requireExplicitClose) {
//          logger().debug("############ KEEP ALIVE ############");
//        }
//        if (!isChannelOpen()) {
//          logger().debug("######### CHANNEL NOT OPEN #########");
//        }
      }
    }

    RequestChannelConsumer consumer() {
      return consumer;
    }

    void confirmCurrentWritable(final ConsumerByteBuffer buffer) {
      try {
        buffer.release();
      } catch (Exception e) {
        // ignore
      }
      try {
        setWriteMode(false);
      } catch (Exception e) {
        // ignore
      }
      writables.poll();
    }

    boolean hasNextWritable() {
      return writables.peek() != null;
    }

    void requireExplicitClose(final boolean option) {
//      logger().debug("######### REQUIRE EXPLICIT CLOSE ######### >>> " + option);
      requireExplicitClose = option;
    }

    ConsumerByteBuffer nextWritable() {
      return writables.peek();
    }

    void queueWritable(final ConsumerByteBuffer buffer) {
      writables.add(buffer);

      if (!writeMode) {
        writableContexts.add(this);
      }
    }

    ConsumerByteBuffer requestBuffer() {
      return requestBufferPool.acquire("SocketChannelSelectionProcessorActor#Context");
    }

    void setWriteMode(final boolean on) throws ClosedChannelException {
      final int options = SelectionKey.OP_READ | (on ? SelectionKey.OP_WRITE : 0);

      selector.registerWith(clientChannel, options, this);

      writeMode = on;
    }

//    int writablesCount() {
//      return writables.size();
//    }
  }
}
