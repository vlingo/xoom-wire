//// Copyright © 2012-2020 VLINGO LABS. All rights reserved.
////
//// This Source Code Form is subject to the terms of the
//// Mozilla Public License, v. 2.0. If a copy of the MPL
//// was not distributed with this file, You can obtain
//// one at https://mozilla.org/MPL/2.0/.
//package io.vlingo.wire.fdx.bidirectional.netty.client;
//
//import io.netty.bootstrap.ServerBootstrap;
//import io.netty.buffer.ByteBuf;
//import io.netty.channel.ChannelFuture;
//import io.netty.channel.ChannelHandlerContext;
//import io.netty.channel.ChannelInboundHandlerAdapter;
//import io.netty.channel.ChannelInitializer;
//import io.netty.channel.nio.NioEventLoopGroup;
//import io.netty.channel.socket.SocketChannel;
//import io.netty.channel.socket.nio.NioServerSocketChannel;
//import io.vlingo.actors.testkit.TestUntil;
//import io.vlingo.wire.channel.ResponseChannelConsumer;
//import io.vlingo.wire.fdx.bidirectional.TestRequestChannelConsumer;
//import io.vlingo.wire.fdx.bidirectional.TestResponseChannelConsumer;
//import io.vlingo.wire.node.Address;
//import io.vlingo.wire.node.AddressType;
//import io.vlingo.wire.node.Host;
//import org.junit.Assert;
//import org.junit.Test;
//
//import java.net.ConnectException;
//import java.nio.ByteBuffer;
//import java.nio.charset.Charset;
//import java.time.Duration;
//import java.util.Set;
//import java.util.UUID;
//import java.util.concurrent.CopyOnWriteArraySet;
//import java.util.concurrent.CountDownLatch;
//import java.util.concurrent.TimeUnit;
//import java.util.concurrent.atomic.AtomicInteger;
//
//public class NettyClientRequestResponseChannelTest {
//  private static AtomicInteger TEST_PORT = new AtomicInteger(37370);
//
//  @Test
//  public void testServerNotAvailable() {
//    final ResponseChannelConsumer consumer = buffer -> {
//      Assert.fail("No replies are expected");
//    };
//
//    final Address address = Address.from(Host.of("localhost"), 8080, AddressType.MAIN);
//
//    final NettyClientRequestResponseChannel clientChannel = new NettyClientRequestResponseChannel(address, consumer, 1, 1, Duration.ofMillis(10),
//                                                                                                  Duration.ofMillis(1), Duration.ofMillis(1));
//
//    try {
//      clientChannel.requestWith(ByteBuffer.wrap(UUID.randomUUID()
//                                                    .toString()
//                                                    .getBytes()));
//    } catch (Exception e) {
//      Assert.assertTrue(e.getCause() instanceof ConnectException);
//    }
//  }
//
//  @Test
//  public void testServerRequestReply() throws InterruptedException {
//    final int nrExpectedMessages = 200;
//    final int requestMsgSize = 36;   //The length of UUID
//    final int replyMsSize = 36 + 6;  //The length of request + length of " reply"
//
//    final CountDownLatch connectionsCount = new CountDownLatch(1);
//    final CountDownLatch serverReceivedMessagesCount = new CountDownLatch(nrExpectedMessages);
//
//    final Set<String> serverReceivedMessage = new CopyOnWriteArraySet<>();
//    final Set<String> serverSentMessages = new CopyOnWriteArraySet<>();
//
//    final Set<String> clientSentMessages = new CopyOnWriteArraySet<>();
//
//    ChannelFuture server = null;
//    final NioEventLoopGroup parentGroup = new NioEventLoopGroup(2);
//    final NioEventLoopGroup childGroup = new NioEventLoopGroup(2);
//
//    try {
//      final int testPort = TEST_PORT.getAndIncrement();
//
//      server = bootstrapServer(requestMsgSize, connectionsCount, serverReceivedMessagesCount, serverReceivedMessage, serverSentMessages, parentGroup,
//                               childGroup, testPort);
//
//      final TestResponseChannelConsumer clientConsumer = new TestResponseChannelConsumer();
//      clientConsumer.currentExpectedResponseLength = replyMsSize;
//      clientConsumer.state = new TestResponseChannelConsumer.State(nrExpectedMessages);
//
//      final Address address = Address.from(Host.of("localhost"), testPort, AddressType.MAIN);
//
//      final NettyClientRequestResponseChannel clientChannel = new NettyClientRequestResponseChannel(address, clientConsumer, 10, replyMsSize);
//
//      for (int i = 0; i < nrExpectedMessages; i++) {
//        final String request = UUID.randomUUID()
//                                   .toString();
//        clientSentMessages.add(request);
//        clientChannel.requestWith(ByteBuffer.wrap(request.getBytes()));
//      }
//
//      Assert.assertTrue("Server should have received connection request", connectionsCount.await(2, TimeUnit.SECONDS));
//      Assert.assertTrue("Server should have received all messages.", serverReceivedMessagesCount.await(4, TimeUnit.SECONDS));
//
//      Assert.assertTrue("Client should have received all server replies", clientConsumer.untilConsume.completesWithin(TimeUnit.SECONDS.toMillis(4)));
//
//      clientSentMessages.forEach(clientRequest -> {
//        Assert.assertTrue("Server should have received request: " + clientRequest, serverReceivedMessage.contains(clientRequest));
//      });
//
//      serverSentMessages.forEach(serverReply -> {
//        Assert.assertTrue("Client should have received reply: " + serverReply, clientConsumer.responses.contains(serverReply));
//      });
//
//    } finally {
//      if (server != null) {
//        server.cancel(true);
//      }
//      parentGroup.shutdownGracefully()
//                 .await();
//      childGroup.shutdownGracefully()
//                .await();
//    }
//  }
//
//  private ChannelFuture bootstrapServer(final int requestMsgSize, final CountDownLatch connectionsCount, final CountDownLatch serverReceivedMessagesCount,
//                                        final Set<String> serverReceivedMessage, final Set<String> serverSentMessages, final NioEventLoopGroup parentGroup,
//                                        final NioEventLoopGroup childGroup, final int testPort) throws InterruptedException {
//    final ServerBootstrap b = new ServerBootstrap();
//
//    return b.group(parentGroup, childGroup)
//            .channel(NioServerSocketChannel.class)
//            .childHandler(new ChannelInitializer<SocketChannel>() {
//              @Override
//              public void initChannel(SocketChannel ch) throws Exception {
//                ch.pipeline()
//                  .addLast(new ChannelInboundHandlerAdapter() {
//                    private byte[] partialRequestBytes;
//
//                    @Override
//                    public void channelActive(final ChannelHandlerContext ctx) throws Exception {
//                      super.channelActive(ctx);
//                      connectionsCount.countDown();
//                    }
//
//                    @Override
//                    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
//                      final ByteBuf byteBuf = (ByteBuf) msg;
//
//                      while (byteBuf.readableBytes() >= requestMsgSize) {
//                        final String request;
//                        if (partialRequestBytes != null && partialRequestBytes.length > 0) {
//                          final int remainingBytes = requestMsgSize - partialRequestBytes.length;
//                          request = new String(partialRequestBytes) + byteBuf.readCharSequence(remainingBytes, Charset.defaultCharset())
//                                                                             .toString();
//                          partialRequestBytes = null;
//                        } else {
//                          request = byteBuf.readCharSequence(requestMsgSize, Charset.defaultCharset())
//                                           .toString();
//                        }
//
//                        serverReceivedMessagesCount.countDown();
//                        serverReceivedMessage.add(request);
//
//                        final String reply = request + " reply";
//
//                        serverSentMessages.add(reply);
//
//                        final ByteBuf replyBuffer = ctx.alloc()
//                                                       .buffer(reply.getBytes().length);
//
//                        replyBuffer.writeBytes(reply.getBytes());
//
//                        ctx.writeAndFlush(replyBuffer);
//                      }
//
//                      if (byteBuf.readableBytes() > 0) {
//                        partialRequestBytes = new byte[byteBuf.readableBytes()];
//                        byteBuf.readBytes(partialRequestBytes);
//                      }
//                    }
//                  });
//              }
//            })
//            .bind(testPort)
//            .sync()
//            .await();
//  }
//
//}