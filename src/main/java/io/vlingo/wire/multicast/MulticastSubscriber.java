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
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.MembershipKey;
import java.util.Enumeration;

import io.vlingo.actors.Logger;
import io.vlingo.wire.channel.ChannelMessageDispatcher;
import io.vlingo.wire.channel.ChannelReader;
import io.vlingo.wire.channel.ChannelReaderConsumer;
import io.vlingo.wire.message.RawMessage;

public class MulticastSubscriber implements ChannelReader, ChannelMessageDispatcher {
  private final ByteBuffer buffer;
  private boolean closed;
  private final DatagramChannel channel;
  private ChannelReaderConsumer consumer;
  private final InetAddress groupAddress;
  private final Logger logger;
  private final MembershipKey membershipKey;
  private final RawMessage message;
  private final String name;
  private final NetworkInterface networkInterface;

  public MulticastSubscriber(
          final String name,
          final Group group,
          final int maxMessageSize,
          final long probeTimeout,
          final Logger logger)
  throws IOException {
    
    this.name = name;
    this.logger = logger;
    
    this.channel = DatagramChannel.open(StandardProtocolFamily.INET);
    this.channel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
    this.channel.bind(new InetSocketAddress(group.port()));
    this.networkInterface = assignNetworkInterfaceTo(this.channel);
    this.groupAddress = InetAddress.getByName(group.address());
    this.membershipKey = channel.join(groupAddress, networkInterface);
    
    this.channel.configureBlocking(false);
    
    this.buffer = ByteBuffer.allocate(maxMessageSize);
    this.message = new RawMessage(maxMessageSize);
    
    logger.log("MulticastSubscriber joined: " + membershipKey);
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
      logger.log("Failed to close channel for: '" + name + "'", e);
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
        buffer.clear();
        final SocketAddress sourceAddress = channel.receive(buffer);
        if (sourceAddress != null) {
          buffer.flip();
          buffer.position(0);
          message.from(buffer);
          
          consumer.consume(message);
        }
    } catch (IOException e) {
      logger.log("Failed to read channel selector for: '" + name + "'", e);
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
  
  private NetworkInterface assignNetworkInterfaceTo(final DatagramChannel channel) throws SocketException, IOException {
    NetworkInterface networkInterface = null;
    
    final Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
    
    while (networkInterfaces.hasMoreElements()) {
      NetworkInterface candidate = networkInterfaces.nextElement();
      
      if (!candidate.getName().contains("virtual") && !candidate.getName().startsWith("v")) {
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
      throw new IOException("Cannot find network interface");
    }
    
    return networkInterface;
  }
}
