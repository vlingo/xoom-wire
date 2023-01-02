// Copyright © 2012-2023 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.xoom.wire.fdx.bidirectional;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;

import io.vlingo.xoom.actors.Logger;
import io.vlingo.xoom.common.Tuple3;
import io.vlingo.xoom.common.pool.ElasticResourcePool;
import io.vlingo.xoom.common.pool.ResourcePool;
import io.vlingo.xoom.wire.channel.RefreshableSelector;
import io.vlingo.xoom.wire.channel.ResponseChannelConsumer;
import io.vlingo.xoom.wire.message.ConsumerByteBuffer;
import io.vlingo.xoom.wire.message.ConsumerByteBufferPool;
import io.vlingo.xoom.wire.node.Address;

/**
 * SecureClientRequestResponseChannel provides SSL for the ClientRequestResponseChannel.
 * <p>
 * Based on:
 * https://examples.javacodegeeks.com/core-java/nio/java-nio-ssl-example/
 * <p>
 * NOTE: There is a lot of buffer copying going on here and time should
 * be invested into reducing that.
 */
public class SecureClientRequestResponseChannel implements ClientRequestResponseChannel {
  private final Address address;
  private final SocketChannel channel;
  private final ResponseChannelConsumer consumer;
  private final Logger logger;
  private final ConsumerByteBufferPool readBufferPool;
  private final RefreshableSelector selector;
  private final SSLProvider sslProvider;
  protected final Queue<ByteBuffer> writeQueue;

  private AtomicBoolean closed;

  public SecureClientRequestResponseChannel(
          final Address address,
          final ResponseChannelConsumer consumer,
          final int maxBufferPoolSize,
          final int maxMessageSize,
          final Logger logger)
  throws Exception {

    logger.debug("SecureClientRequestResponseChannel: Initializing");

    this.address = address;
    this.consumer = consumer;
    this.logger = logger;
    this.readBufferPool = new ConsumerByteBufferPool(
        ElasticResourcePool.Config.of(maxBufferPoolSize), maxMessageSize);

    this.closed = new AtomicBoolean(false);
    this.writeQueue = new ConcurrentLinkedQueue<>();

    final Tuple3<SocketChannel, SSLProvider, RefreshableSelector> quad = connect(address);
    this.channel = quad._1;
    this.sslProvider = quad._2;
    this.selector = quad._3;
  }

  @Override
  public void close() {
    logger.debug("SecureClientRequestResponseChannel: Closing");

    if (!isClosed()) {
      try {
        selector.close();
        channel.close();
      } catch (Exception e) {
        logger.error("Failed to close channel to " + address + " because: " + e.getMessage(), e);
      }
    }
    closed.set(true);
  }

  public boolean isClosed() {
    return closed.get();
  }

  @Override
  public void requestWith(final ByteBuffer buffer) {
    logger.debug("SecureClientRequestResponseChannel: Requesting");

    writeQueue.add(buffer);
  }

  @Override
  public void probeChannel() {
    if (isClosed()) {
      return;
    }

//  logger.debug("SecureClientRequestResponseChannel: Probing");

    try {
      Iterator<SelectionKey> keys = selector.selectNow();

//    logger.debug("SecureClientRequestResponseChannel: Probing selector");

      while (keys.hasNext()) {
        final SelectionKey key = keys.next();
        keys.remove();

        if (key.isValid()) {
          if (key.isReadable()) {
//          logger.debug("SecureClientRequestResponseChannel: Probing selector read key");
            sslProvider.read();
          } else if (key.isWritable()) {
//          logger.debug("SecureClientRequestResponseChannel: Probing selector write key");
            if (sslProvider.ready.get()) {
              while (true) {
                final ByteBuffer toSend = this.writeQueue.poll();
                if (toSend != null) {
//                logger.debug("SecureClientRequestResponseChannel: Writing");
                  sslProvider.write(toSend);
                } else {
                  break;
                }
              }
            }
          }
        }
      }
    } catch (Exception e) {
      logger.error("Failed secure client channel processing for because: " + e.getMessage(), e);
    }
  }

  private Tuple3<SocketChannel, SSLProvider, RefreshableSelector> connect(final Address address) throws Exception {
    // channel
    final RefreshableSelector selector = RefreshableSelector.open(address.toString());
    final SocketChannel channel = SocketChannel.open();
    final InetSocketAddress hostAddress = new InetSocketAddress(address.hostName(), address.port());
    channel.connect(hostAddress);
    channel.configureBlocking(false);
    final SelectionKey selectionKey = selector.registerWith(channel, SelectionKey.OP_CONNECT | SelectionKey.OP_READ | SelectionKey.OP_WRITE);
    final Executor worker = Executors.newSingleThreadExecutor();
    final Executor taskWorkers = Executors.newFixedThreadPool(2);

    // ssl
    final SSLEngine engine = SSLContext.getDefault().createSSLEngine();
    engine.setUseClientMode(true);
    engine.beginHandshake();
    final SSLProvider sslProvider = new SSLProvider(selectionKey, engine, worker, taskWorkers, readBufferPool);

//    logger.debug("SecureClientRequestResponseChannel: Connected");

    return Tuple3.from(channel, sslProvider, selector);
  }

  private class SSLProvider extends SSLWorker {
    private final ByteBuffer buffer = ByteBuffer.allocate(32 * 1024);
    private final SelectionKey key;
    private AtomicBoolean ready;

    public SSLProvider(final SelectionKey key, final SSLEngine engine, final Executor ioWorker, final Executor taskWorkers, final ResourcePool<ConsumerByteBuffer, String> readBufferPool) {
      super(engine, ioWorker, taskWorkers, readBufferPool);
      this.key = key;
      this.ready = new AtomicBoolean(false);
    }

    @Override
    public void onFailure(final Exception e) {
      logger.error("SecureClientRequestResponseChannel.SSLProvider: Failed Handshake because: " + e.getMessage(), e);
    }

    @Override
    public void onSuccess() {
      logger.error("SecureClientRequestResponseChannel.SSLProvider: Handshake Succeeded");
      final SSLSession session = engine.getSession();
      try {
        ready.set(true);
        logger.debug("SecureClientRequestResponseChannel.SSLProvider: local principal: " + session.getLocalPrincipal());
        logger.debug("SecureClientRequestResponseChannel.SSLProvider: remote principal: " + session.getPeerPrincipal());
        logger.debug("SecureClientRequestResponseChannel.SSLProvider: cipher: " + session.getCipherSuite());
      } catch (Exception e) {
        logger.warn("SecureClientRequestResponseChannel.SSLProvider: Failed Session report because: " + e.getMessage(), e);
      }
    }

    @Override
    public void onInput(final ByteBuffer decrypted) {
      final ConsumerByteBuffer buffer = readBufferPool.acquire("SecureClientRequestResponseChannel#SSLProvider#onInput");
      consumer.consume(buffer.put(decrypted).flip());
    }

    @Override
    public void onOutput(final ByteBuffer encrypted) {
      try {
        ((WritableByteChannel) this.key.channel()).write(encrypted);
      } catch (IOException exc) {
        throw new IllegalStateException(exc);
      }
    }

    @Override
    public void onClosed() {
      logger.debug("SecureClientRequestResponseChannel.SSLProvider: closed");
    }

    public boolean read() {
      buffer.clear();
      int bytes;
      try {
        bytes = ((ReadableByteChannel) this.key.channel()).read(buffer);
      } catch (IOException ex) {
        bytes = -1;
      }
      if (bytes == -1) {
        return false;
      }
      buffer.flip();
      final ByteBuffer copy = ByteBuffer.allocate(bytes);
      copy.put(buffer);
      copy.flip();
      this.notify(copy);
      return true;
    }
  }

  private abstract class SSLWorker implements Runnable {
    final SSLEngine engine;
    final Executor ioWorker, taskWorkers;
    final ByteBuffer clientWrap, clientUnwrap;
    final ByteBuffer serverWrap, serverUnwrap;
    final ResourcePool<ConsumerByteBuffer, String> readBufferPool;

    private final AtomicBoolean handShakeLock;

    public SSLWorker(SSLEngine engine, Executor ioWorker, Executor taskWorkers, final ResourcePool<ConsumerByteBuffer, String> readBufferPool) {
      this.handShakeLock = new AtomicBoolean(false);
      this.readBufferPool = readBufferPool;

      // TODO investigate how the "leaked" buffers below affect the pool's ability to compact
      this.clientWrap = readBufferPool.acquire("SecureClientRequestResponseChannel#SSLWorker#clientWrap").asByteBuffer();
      this.serverWrap = readBufferPool.acquire("SecureClientRequestResponseChannel#SSLWorker#serverWrap").asByteBuffer();
      this.clientUnwrap = readBufferPool.acquire("SecureClientRequestResponseChannel#SSLWorker#clientUnwrap").asByteBuffer();
      this.serverUnwrap = readBufferPool.acquire("SecureClientRequestResponseChannel#SSLWorker#serverUnwrap").asByteBuffer();

      this.clientUnwrap.limit(0);
      this.engine = engine;
      this.ioWorker = ioWorker;
      this.taskWorkers = taskWorkers;
      this.ioWorker.execute(this);
    }

    public abstract void onInput(final ByteBuffer decrypted);

    public abstract void onOutput(final ByteBuffer encrypted);

    public abstract void onFailure(final Exception e);

    public abstract void onSuccess();

    public abstract void onClosed();

    public void write(final ByteBuffer data) {
      this.ioWorker.execute(new Runnable() {
        @Override
        public void run() {
          clientWrap.put(data);
          SSLWorker.this.run();
        }
      });
    }

    public void notify(final ByteBuffer data) {
      this.ioWorker.execute(new Runnable() {
        @Override
        public void run() {
          clientUnwrap.put(data);
          SSLWorker.this.run();
        }
      });
    }

    @Override
    public void run() {
      // executes non-blocking tasks on the IO-Worker
      while (this.isHandShaking()) {
        continue;
      }
    }

    private boolean isHandShaking() {
      try {
        while (!handShakeLock.compareAndSet(false, true)) ;

        switch (engine.getHandshakeStatus()) {
        case NOT_HANDSHAKING: {
          boolean occupied = false;
          if (clientWrap.position() > 0) occupied |= this.wrap();
          if (clientUnwrap.position() > 0) occupied |= this.unwrap();
          return occupied;
        }

        case NEED_WRAP:
          if (!this.wrap()) {
            return false;
          }
          break;

        case NEED_UNWRAP:
          if (!this.unwrap()) {
            return false;
          }
          break;

        case NEED_TASK:
          final Runnable sslTask = engine.getDelegatedTask();
          if (sslTask != null) {
            final Runnable wrappedTask = new Runnable() {
              @Override
              public void run() {
                sslTask.run();
                ioWorker.execute(SSLWorker.this);
              }
            };
            taskWorkers.execute(wrappedTask);
            return false;
          }

        case FINISHED:
          throw new IllegalStateException("FINISHED");
        }

        return true;
      } finally {
        handShakeLock.set(false);
      }
    }

    private boolean wrap() {
      final SSLEngineResult wrapResult;

      try {
        clientWrap.flip();
        wrapResult = engine.wrap(clientWrap, serverWrap);
        clientWrap.compact();
      } catch (SSLException exc) {
        this.onFailure(exc);
        return false;
      }

      switch (wrapResult.getStatus()) {
      case OK:
        if (serverWrap.position() > 0) {
          serverWrap.flip();
          this.onOutput(serverWrap);
          serverWrap.compact();
        }
        break;

      case BUFFER_UNDERFLOW:
        // try again later
        break;

      case BUFFER_OVERFLOW:
        throw new IllegalStateException("failed to wrap");

      case CLOSED:
        logger.debug("SecureClientRequestResponseChannel.SSLProvider: wrap closed");
        this.onClosed();
        return false;
      }

      return true;
    }

    private boolean unwrap() {
      final SSLEngineResult unwrapResult;

      try {
        clientUnwrap.flip();
        unwrapResult = engine.unwrap(clientUnwrap, serverUnwrap);
        clientUnwrap.compact();
      } catch (SSLException ex) {
        this.onFailure(ex);
        return false;
      }

      switch (unwrapResult.getStatus()) {
      case OK:
        if (serverUnwrap.position() > 0) {
          serverUnwrap.flip();
          this.onInput(serverUnwrap);
          serverUnwrap.compact();
        }
        break;

      case CLOSED:
        logger.debug("SecureClientRequestResponseChannel.SSLProvider: unwrap closed");
        this.onClosed();
        return false;

      case BUFFER_OVERFLOW:
        throw new IllegalStateException("failed to unwrap");

      case BUFFER_UNDERFLOW:
        return false;
      }

      if (unwrapResult.getHandshakeStatus() == HandshakeStatus.FINISHED) {
        this.onSuccess();
        return false;
      }

      return true;
    }
  }
}
