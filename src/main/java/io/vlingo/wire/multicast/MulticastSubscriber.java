// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.multicast;

import io.vlingo.actors.Logger;
import io.vlingo.wire.channel.ChannelMessageDispatcher;
import io.vlingo.wire.channel.ChannelReader;
import io.vlingo.wire.channel.ChannelReaderConsumer;
import io.vlingo.wire.message.ByteBufferAllocator;
import io.vlingo.wire.message.RawMessage;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.MembershipKey;
import java.util.Enumeration;

public class MulticastSubscriber implements ChannelReader, ChannelMessageDispatcher {
  private final ByteBuffer buffer;
  private boolean closed;
  private final DatagramChannel channel;
  private ChannelReaderConsumer consumer;
  private final InetAddress groupAddress;
  private final Logger logger;
  private final int maxReceives;
  private final MembershipKey membershipKey;
  private final RawMessage message;
  private final String name;
  private final NetworkInterface networkInterface;

  public MulticastSubscriber(
          final String name,
          final Group group,
          final int maxMessageSize,
          final int maxReceives,
          final Logger logger)
  throws IOException {
    this(name, group, null, maxMessageSize, maxReceives, logger);
  }

  public MulticastSubscriber(
          final String name,
          final Group group,
          final String networkInterfaceName,
          final int maxMessageSize,
          final int maxReceives,
          final Logger logger)
  throws IOException {
    
    this.name = name;
    this.logger = logger;
    
    this.channel = DatagramChannel.open(StandardProtocolFamily.INET);
    this.channel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
    this.channel.bind(new InetSocketAddress(group.port()));
    this.networkInterface = assignNetworkInterfaceTo(this.channel, networkInterfaceName);
    this.groupAddress = InetAddress.getByName(group.address());
    this.membershipKey = channel.join(groupAddress, networkInterface);
    
    this.channel.configureBlocking(false);
    
    this.buffer = ByteBufferAllocator.allocate(maxMessageSize);
    this.message = new RawMessage(maxMessageSize);
    
    this.maxReceives = maxReceives;
    
    logger.info("MulticastSubscriber joined: " + membershipKey);
  }
  
  //=========================================
  // ChannelReader
  //=========================================
  
  @Override
  public void close() {
    if (closed) return;
    
    closed = true;
    
    try {
      channel.close();
    } catch (Exception e) {
      logger.error("Failed to close channel for: '" + name + "'", e);
    }
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public void openFor(final ChannelReaderConsumer consumer) throws IOException {
    if (closed) return; // for some tests it's possible to receive close() before start()
    
    this.consumer = consumer;
  }

  @Override
  public void probeChannel() {
    if (closed) return;
    
    try {
      // when nothing is received, receives represents retries
      // and possibly some number of receives
      for (int receives = 0; receives < maxReceives; ++receives) {
        buffer.clear();
        final SocketAddress sourceAddress = channel.receive(buffer);
        if (sourceAddress != null) {
          buffer.flip();
          message.from(buffer);
          
          consumer.consume(message);
        }
      }
    } catch (IOException e) {
      logger.error("Failed to read channel selector for: '" + name + "'", e);
    }
  }

  //=========================================
  // ChannelMessageDispatcher
  //=========================================
  
  @Override
  public ChannelReaderConsumer consumer() {
    return consumer;
  }

  @Override
  public Logger logger() {
    return logger;
  }

  //=========================================
  // internal implementation
  //=========================================
  
  private NetworkInterface assignNetworkInterfaceTo(
          final DatagramChannel channel,
          final String networkInterfaceName)
  throws IOException {
    
    if (networkInterfaceName != null && !networkInterfaceName.trim().isEmpty()) {
      final NetworkInterface specified = NetworkInterface.getByName(networkInterfaceName);
      if (specified != null) {
        channel.setOption(StandardSocketOptions.IP_MULTICAST_IF, specified);
        return specified;
      }
    }

    // if networkInterfaceName not given or unknown, take best guess
    
    return assignBestGuessNetworkInterfaceTo(channel);
  }

  private NetworkInterface assignBestGuessNetworkInterfaceTo(
          final DatagramChannel channel)
  throws IOException {
    
    NetworkInterface networkInterface = null;
    
    final Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
    
    while (networkInterfaces.hasMoreElements()) {
      NetworkInterface candidate = networkInterfaces.nextElement();
      final String candidateName = candidate.getName().toLowerCase();
      if (!candidateName.contains("virtual") && !candidateName.startsWith("v")) {
        if (candidate.isUp() && !candidate.isLoopback() && !candidate.isPointToPoint() && !candidate.isVirtual()) {
          try {
            channel.setOption(StandardSocketOptions.IP_MULTICAST_IF, candidate);
            networkInterface = candidate;
            break;
          } catch (IOException e) {
            networkInterface = null;
          }
        }
      }
    }
    
    if (networkInterface == null) {
      throw new IOException("Cannot assign network interface");
    }
    
    return networkInterface;
  }
}
