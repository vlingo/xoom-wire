// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.fdx.bidirectional;

import io.vlingo.actors.Logger;
import io.vlingo.wire.channel.ResponseChannelConsumer;
import io.vlingo.wire.node.Address;
import org.baswell.niossl.NioSslLogger;
import org.baswell.niossl.SSLSocketChannel;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class SecureClientRequestResponseChannel extends ClientRequestResponseChannel {
  private int previousPrepareFailures;

  public SecureClientRequestResponseChannel(
          final Address address,
          final ResponseChannelConsumer consumer,
          final int maxBufferPoolSize,
          final int maxMessageSize,
          final Logger logger)
  throws Exception {
    super(address, consumer, maxBufferPoolSize, maxMessageSize, new LoggerAdapter(logger));

    this.previousPrepareFailures = 0;
  }

  /**
   * @see io.vlingo.wire.fdx.bidirectional.ClientRequestResponseChannel#preparedChannelDelegate()
   */
  @Override
  protected SocketChannel preparedChannelDelegate() {
    SocketChannel channel = channel();

    try {
      if (channel != null) {
        if (channel.isConnected()) {
          previousPrepareFailures = 0;
          return channel;
        } else {
          closeChannel();
        }
      }
      channel = open();
      previousPrepareFailures = 0;
      return channel;
    } catch (Exception e) {
      closeChannel();
      final String message = getClass().getSimpleName() + ": Cannot prepare/open channel because: " + e.getMessage();
      if (previousPrepareFailures == 0) {
        logger().error(message, e);
      } else if (previousPrepareFailures % 20 == 0) {
        logger().info("AGAIN: " + message);
      }
    }
    ++previousPrepareFailures;
    return null;
  }

  private SSLSocketChannel open() throws Exception {
    final SocketChannel socketChannel = SocketChannel.open(new InetSocketAddress(address.hostName(), address.port()));

    socketChannel.configureBlocking(false);

    final SSLContext sslContext = SSLContext.getDefault(); // .getInstance("TLS");
    final SSLEngine sslEngine = sslContext.createSSLEngine();
    sslEngine.setUseClientMode(true);

    final int minAppBufferSize = sslEngine.getSession().getApplicationBufferSize();

    maxMessageSize(Integer.max(maxMessageSize(), minAppBufferSize));

    final ThreadPoolExecutor sslThreadPool = new ThreadPoolExecutor(2, 2, 25, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());

    final SSLSocketChannel sslSocketChannel = new SSLSocketChannel(socketChannel, sslEngine, sslThreadPool, (NioSslLogger) logger);

    return sslSocketChannel;
  }

  private static class LoggerAdapter implements Logger, NioSslLogger {
    private final Logger logger;

    private LoggerAdapter(final Logger logger) {
      this.logger = logger;
    }

    @Override
    public void close() {
      logger.close();
    }

    @Override
    public boolean isEnabled() {
      return logger.isEnabled();
    }

    @Override
    public void log(final String message) {
      logger.debug(message);
    }

    @Override
    public void log(final String message, final Throwable throwable) {
      logger.debug(message, throwable);
    }

    @Override
    public String name() {
      return logger.name();
    }

    @Override
    public void error(final String message) {
      logger.error(message);
    }

    @Override
    public void error(final String message, final Object... args) {
      logger.warn(message, args);
    }

    @Override
    public void error(final String message, final Throwable exception) {
      logger.error(message, exception);
    }

    @Override
    public void trace(final String message) {
      logger.trace(message);
    }

    @Override
    public void trace(final String message, final Object... args) {
      logger.trace(message, args);
    }

    @Override
    public void trace(final String message, final Throwable throwable) {
      logger.trace(message, throwable);
    }

    @Override
    public void debug(final String message, final Object... args) {
      logger.debug(message, args);
    }

    @Override
    public void debug(final String message, final Throwable throwable) {
      logger.debug(message, throwable);
    }

    @Override
    public void info(final String message) {
      logger.info(message);
    }

    @Override
    public void info(final String message, final Object... args) {
      logger.info(message, args);
    }

    @Override
    public void info(final String message, final Throwable throwable) {
      logger.info(message, throwable);
    }

    @Override
    public void warn(final String message) {
      logger.warn(message);
    }

    @Override
    public void warn(final String message, final Object... args) {
      logger.warn(message, args);
    }

    @Override
    public void warn(final String message, final Throwable throwable) {
      logger.warn(message, throwable);
    }

    @Override
    public boolean logDebugs() {
      return true;
    }

    @Override
    public void debug(final String message) {
      logger.debug(message);
    }
  }
}
