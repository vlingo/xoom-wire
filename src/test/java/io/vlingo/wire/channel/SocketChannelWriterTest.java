// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.channel;

import io.vlingo.actors.Logger;

import io.vlingo.actors.testkit.AccessSafely;
import io.vlingo.wire.fdx.inbound.tcp.SocketChannelInboundReader;
import io.vlingo.wire.message.AbstractMessageTool;
import io.vlingo.wire.message.ByteBufferAllocator;
import io.vlingo.wire.message.RawMessage;
import io.vlingo.wire.node.Host;
import io.vlingo.wire.node.Id;
import io.vlingo.wire.node.Name;
import io.vlingo.wire.node.Node;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.ByteBuffer;

import static org.junit.Assert.assertEquals;

public class SocketChannelWriterTest extends AbstractMessageTool {
  private static final String TestMessage = "TEST ";
  
  private SocketChannelWriter channelWriter;
  private ChannelReader channelReader;
  
  @Test
  public void testChannelWriter() throws Exception {
    final MockChannelReaderConsumer consumer = new MockChannelReaderConsumer();
    final AccessSafely consumerAccess = consumer.afterCompleting(0);
    
    channelReader.openFor(consumer);
    
    final ByteBuffer buffer = ByteBufferAllocator.allocate(1024);
    
    final String message1 = TestMessage + 1;
    final RawMessage rawMessage1 = RawMessage.from(0, 0, message1);
    channelWriter.write(rawMessage1, buffer);
    
    probeUntilConsumed(channelReader, consumerAccess);
    
    assertEquals(1, (int)consumerAccess.readFrom("consumeCount"));
    assertEquals(message1, consumerAccess.readFrom("message", 0));

    final String message2 = TestMessage + 2;
    final RawMessage rawMessage2 = RawMessage.from(0, 0, message2);
    channelWriter.write(rawMessage2, buffer);
    
    probeUntilConsumed(channelReader, consumerAccess);
    
    assertEquals(2, (int)consumerAccess.readFrom("consumeCount"));
    assertEquals(message2, consumerAccess.readFrom("message", 1));
  }
  
  @Before
  public void setUp() throws Exception {
    final Node node = Node.with(Id.of(2), Name.of("node2"), Host.of("localhost"), 37373, 37374);
    final Logger logger = Logger.basicLogger();
    channelWriter = new SocketChannelWriter(node.operationalAddress(), logger);
    channelReader = new SocketChannelInboundReader(node.operationalAddress().port(), "test-reader", 1024, logger);
  }
  
  @After
  public void tearDown() {
    channelWriter.close();
    channelReader.close();
  }

  private void probeUntilConsumed(final ChannelReader reader, final AccessSafely consumerAccess ) {
    final int currentConsumedCount = consumerAccess.readFrom("consumeCount");
    
    for (int idx = 0; idx < 100; ++idx) {
      reader.probeChannel();
      
      if ((int)consumerAccess.readFrom("consumeCount") > currentConsumedCount) break;
    }
  }
}
