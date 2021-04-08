// Copyright © 2012-2021 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.xoom.wire.channel;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Collections;
import java.util.Iterator;

import io.vlingo.xoom.actors.Logger;

/**
 * Provides NIO {@code Selector} behavior with the potential for managed refreshes of the
 * {@code Selector} with {@code Channel} instances. The refresh is based on the fix to Netty to
 * work around the perpetual Linux JDK bug exhibited when using channel selections with timeouts.
 * <p>
 * NOTE: To use this service with a threshold you must initialize with {@code withCountedThreshold()}
 * or {@code withTimedThreshold()} before a dependent component initializes with
 * {@code withNoThreshold()}. Notably the xoom-http {@code Server} will initialize with
 * {@code withNoThreshold()} unless this service is initialized earlier with a threshold. Thus,
 * to use a threshold, initialize this service just following the {@code World} start up.
 * <p>
 * See https://github.com/vlingo/xoom-wire/issues/28
 */
public class RefreshableSelector {
  /** The types of refresh thresholds supported. */
  private static enum ThresholdType { Counted, None, Timed };

  /** The empty Iterator to return when there are no selection keys available. */
  private static Iterator<SelectionKey> EmptyIterator = Collections.emptyListIterator();

  /** The Logger used by all instances. */
  private static Logger logger;

  /** The threshold value used by all instances. */
  private static long threshold;

  /** The threshold type used by all instances. */
  private static ThresholdType thresholdType;

  /** My name. */
  private final String name;

  /** My count for how many times selector was refreshed. */
  private long refreshedCount;

  /** My Selector. */
  private Selector selector;

  /** My value to track progress toward refreshing. */
  private long trackingValue;

  /**
   * Initialize the {@code RefreshableSelector} service with a count threshold.
   * @param maximumSelects the int maximum number of selects before a refresh
   * @param logger the Logger used by all instances
   */
  public static void withCountedThreshold(final int maximumSelects, final Logger logger) {
    initializeWith(ThresholdType.Counted, maximumSelects, logger);
  }

  /**
   * Initialize the {@code RefreshableSelector} service with no threshold.
   * @param logger the Logger used by all instances
   */
  public static void withNoThreshold(final Logger logger) {
    initializeWith(ThresholdType.None, 0, logger);
  }

  /**
   * Initialize the {@code RefreshableSelector} service with a time threshold.
   * @param milliseconds the long maximum number of milliseconds between any number of selects before a refresh
   * @param logger the Logger used by all instances
   */
  public static void withTimedThreshold(final long milliseconds, final Logger logger) {
    initializeWith(ThresholdType.Timed, milliseconds, logger);
  }

  /**
   * Answer a new {@code RefreshableSelector} with the given {@code name}.
   * @param name the String name to assign to the new RefreshableSelector
   * @return RefreshableSelector
   */
  public static RefreshableSelector open(final String name) {
    return new RefreshableSelector(name);
  }

  /**
   * Close my {@code selector}.
   * @throws IOException when close fails
   */
  public void close() throws IOException {
    selector.close();
  }

  /**
   * Answer the {@code SelectionKey} for the {@code channel} with my {@code Selector}.
   * @param channel the SelectableChannel within with the SelectionKey exists for my Selector
   * @return SelectionKey
   * @throws IOException when key retrieval fails
   */
  public SelectionKey keyFor(final SelectableChannel channel) throws IOException {
    return channel.keyFor(selector);
  }

  /**
   * Answer the new SelectionKey that results from registering my selector with the {@code channel}
   * using the given {@code options}.
   * @param channel the SelectableChannel with which to register my selector
   * @param options the options to associate with the new SelectionKey
   * @return SelectionKey
   * @throws ClosedChannelException when the registration fails
   */
  public SelectionKey registerWith(final SelectableChannel channel, final int options) throws ClosedChannelException {
    return registerWith(channel, options, null);
  }

  /**
   * Answer the new SelectionKey that results from registering my selector with the {@code channel}
   * using the given {@code options} and {@code attachment}.
   * @param channel the SelectableChannel with which to register my selector
   * @param options the options to associate with the new SelectionKey
   * @param attachment the Object to attach to the new SelectionKey
   * @return SelectionKey
   * @throws ClosedChannelException when the registration fails
   */
  public SelectionKey registerWith(final SelectableChannel channel, final int options, final Object attachment) throws ClosedChannelException {
    return channel.register(selector, options, attachment);
  }

  /**
   * Answer a {@code Iterator<SelectionKey>} for the number of available keys,
   * or an empty {@code Iterator<SelectionKey>} if none are available.
   * @param timeout the long number of milliseconds to allow for selecting
   * @return {@code Iterator<SelectionKey>}
   * @throws IOException when the selection fails
   */
  public Iterator<SelectionKey> select(final long timeout) throws IOException {
    switch (thresholdType) {
    case Counted:
      if (trackingValue++ >= threshold) {
        refresh();
        trackingValue = 0;
      }
      break;
    case None:
      break;
    case Timed:
      final long currentTime = System.currentTimeMillis();
      if (currentTime - trackingValue >= threshold) {
        refresh();
        trackingValue = currentTime;
      }
      break;
    }

    if (selector.select(timeout) > 0) {
      return selector.selectedKeys().iterator();
    }

    return EmptyIterator;
  }

  /**
   * Answer immediately a {@code Iterator<SelectionKey>} for the number of available keys,
   * or an empty {@code Iterator<SelectionKey>} if none are available.
   * @return {@code Iterator<SelectionKey>}
   * @throws IOException when the selection fails
   */
  public Iterator<SelectionKey> selectNow() throws IOException {
    return select(1);
  }

  /**
   * Answer my {@code refreshedCount}.
   * @return long
   */
  public long refreshedCount() {
    return refreshedCount;
  }


  //=========================================
  // internal implementation
  //=========================================

  public static void resetForTest() {
    synchronized (RefreshableSelector.class) {
      RefreshableSelector.thresholdType = null;
      RefreshableSelector.threshold = 0;
      RefreshableSelector.logger = null;
    }
  }

  private static void initializeWith(ThresholdType thresholdType, final long threshold, final Logger logger) {
    synchronized (RefreshableSelector.class) {
      if (RefreshableSelector.thresholdType == null) {
        RefreshableSelector.thresholdType = thresholdType;
        RefreshableSelector.threshold = threshold;
        RefreshableSelector.logger = logger;
      }
    }
  }

  private RefreshableSelector(final String name) {
    this.name = name;
    this.refreshedCount = 0;
    this.selector = open();
    this.trackingValue = determineTrackingValue();
  }

  private long determineTrackingValue() {
    switch (thresholdType) {
    case Counted:
      return 0;
    case None:
      return 0;
    case Timed:
      return System.currentTimeMillis();
    default:
      return 0;
    }
  }

  private Selector open() {
    try {
      return Selector.open();
    } catch (Exception e) {
      final String message = "Failed to open selector for '" + name + "' because: " + e.getMessage();
      logger.error(message, e);
      throw new IllegalArgumentException(message);
    }
  }

  private void refresh() {
    final Selector refreshedSelector = open();
    int total = 0;

    for (final SelectionKey key : selector.keys()) {
      final SelectableChannel channel = key.channel();
      final Object attachment = key.attachment();

      try {
        if (!key.isValid() || channel.keyFor(refreshedSelector) != null) {
          continue;
        }

        final int options = key.interestOps();

        key.cancel();

        channel.register(refreshedSelector, options, attachment);

        ++total;
      } catch (Throwable t) {
        warnAbout(getClass().getSimpleName() + ": Did not register channel " + channel.toString() + " to refreshed selector '" + name + "' because of: " + t.getMessage(), t);
      }
    }

    swap(refreshedSelector);

    ++refreshedCount;

    tellAbout("Refreshed " + total + " channels to the refreshed selector '" + name + "'");
  }

  private void swap(final Selector refreshedSelector) {
    final Selector selectorToReplace = selector;

    selector = refreshedSelector;

    try {
      selectorToReplace.close();
    } catch (Exception e) {
      warnAbout(getClass().getSimpleName() + ": Did not close previous selector of '" + name + "' because of: " + e.getMessage(), e);
    }
  }

  private void tellAbout(final String message) {
    if (logger.isEnabled()) {
      logger.info(message);
    }
  }

  private void warnAbout(final String message, final Throwable t) {
    if (logger.isEnabled()) {
      logger.warn(message, t);
    }
  }
}
