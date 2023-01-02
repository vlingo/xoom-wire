// Copyright © 2012-2023 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.
package io.vlingo.xoom.wire.fdx.bidirectional.rsocket;

import io.rsocket.Closeable;
import io.rsocket.Payload;
import io.rsocket.SocketAcceptor;
import io.rsocket.core.RSocketServer;
import io.rsocket.frame.decoder.PayloadDecoder;
import io.rsocket.transport.ClientTransport;
import io.rsocket.transport.local.LocalClientTransport;
import io.rsocket.transport.local.LocalServerTransport;
import io.rsocket.util.DefaultPayload;
import io.vlingo.xoom.actors.Logger;
import io.vlingo.xoom.actors.testkit.AccessSafely;
import io.vlingo.xoom.wire.channel.ResponseChannelConsumer;
import io.vlingo.xoom.wire.node.Address;
import io.vlingo.xoom.wire.node.AddressType;
import io.vlingo.xoom.wire.node.Host;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class RSocketClientChannelTest {
  private static final Logger LOGGER = Logger.basicLogger();
  private final ClientTransport clientTransport = LocalClientTransport.create("rsocket-fdx-client-test");
  private final LocalServerTransport serverTransport = LocalServerTransport.create("rsocket-fdx-client-test");

  @Test
  public void testServerNotAvailable() {
    final ResponseChannelConsumer consumer = buffer -> Assert.fail("No messages are expected");

    final Address address = buildAddress(49240);

    RSocketClientChannel clientChannel = null;
    try {
      clientChannel = buildClientChannel(consumer, address);

      for (int i = 0; i < 10; i++) {
        request(clientChannel, UUID.randomUUID()
                                   .toString());
      }
      //all messages should be dropped
    } finally {
      if (clientChannel != null) {
        clientChannel.close();
      }
    }
  }

  @Test
  public void testServerRequestReply() throws InterruptedException {
    final CountDownLatch countDownLatch = new CountDownLatch(1);
    final CountDownLatch serverReceivedMessages = new CountDownLatch(100);

    Set<String> serverReceivedMessage = new LinkedHashSet<>();

    final SocketAcceptor socketAcceptor = SocketAcceptor.forRequestChannel(payloads -> {
      countDownLatch.countDown();
      return Flux.from(payloads)
              .doOnNext(payload -> {
                serverReceivedMessages.countDown();
                serverReceivedMessage.add(payload.getDataUtf8());
                payload.release();
              })
              .zipWith(Flux.range(1, 100), (payload, integer) -> DefaultPayload.create("Reply " + integer));
    });

    final Closeable server = RSocketServer.create()
                                          .payloadDecoder(PayloadDecoder.ZERO_COPY)
                                          .acceptor(socketAcceptor)
                                          .bind(this.serverTransport)
                                          .block();

    Assert.assertNotNull("Server failed to start", server);
    final Address address = buildAddress(0);

    final CountDownLatch clientReceivedMessages = new CountDownLatch(100);

    Set<String> serverReplies = new LinkedHashSet<>();

    final ResponseChannelConsumer consumer = buffer -> {
      clientReceivedMessages.countDown();
      serverReplies.add(new String(buffer.array(), 0, buffer.remaining()));
    };

    RSocketClientChannel clientChannel = null;

    try {
      Set<String> clientRequests = new LinkedHashSet<>();

      synchronized (clientReceivedMessages) {
        clientChannel = buildClientChannel(consumer, address);

        for (int i = 0; i < 100; i++) {
          final String request = "Request_" + i + "_" + UUID.randomUUID()
                                                            .toString();
          request(clientChannel, request);
          clientRequests.add(request);
        }
      }

      synchronized (clientReceivedMessages) {
        Assert.assertTrue("Server should have received requestChannel request", countDownLatch.await(2, TimeUnit.SECONDS));
        Assert.assertTrue("Server should have received all messages", serverReceivedMessages.await(4, TimeUnit.SECONDS));
        Assert.assertTrue("Client should have received all server replies", clientReceivedMessages.await(4, TimeUnit.SECONDS));

        for (int i = 1; i <= 100; i++) {
          Assert.assertTrue(serverReplies.contains("Reply " + i));
        }

        clientRequests.forEach(clientRequest -> {
          Assert.assertTrue("Server should have received request: " + clientRequest, serverReceivedMessage.contains(clientRequest));
        });
      }
    } finally {
      close(clientChannel, server);
    }
  }

  @Ignore
  @Test
  public void testServerApplicationErrorsProcess() throws InterruptedException {
    final ResponseChannelConsumer consumer = buffer -> Assert.fail("No messages are expected");

    final CountDownLatch countDownLatch = new CountDownLatch(1);
    final CountDownLatch serverReceivedMessages = new CountDownLatch(100);
    final List<String> receivedMessages = new CopyOnWriteArrayList<>();

    final SocketAcceptor acceptor = SocketAcceptor.forRequestChannel(payloads -> {
      countDownLatch.countDown();
      Flux.from(payloads)
              .subscribe(payload -> {
                serverReceivedMessages.countDown();
                receivedMessages.add(payload.getDataUtf8());
              });

      return Flux.error(new RuntimeException("Server exception"));
    });

    final Closeable server = RSocketServer.create()
                                           .payloadDecoder(PayloadDecoder.ZERO_COPY)
                                           .acceptor(acceptor)
                                           .bind(this.serverTransport)
                                           .subscribeOn(Schedulers.single())
                                           .block();

    Assert.assertNotNull("Server failed to start", server);
    final Address address = buildAddress(0);

    RSocketClientChannel clientChannel = null;

    try {
      clientChannel = buildClientChannel(consumer, address);

      final List<String> sentMessages = new ArrayList<>(100);
      for (int i = 0; i < 100; i++) {
        final String sentMessage = UUID.randomUUID()
                                       .toString();
        sentMessages.add(sentMessage);
        request(clientChannel, sentMessage);
      }

      Assert.assertTrue("Server should have received requestChannel request", countDownLatch.await(15, TimeUnit.SECONDS));
      Assert.assertTrue("Server should have received all messages", serverReceivedMessages.await(15, TimeUnit.SECONDS));
      sentMessages.forEach(expectedMsg -> {
        Assert.assertTrue("Server should have received message " + expectedMsg, receivedMessages.contains(expectedMsg));
      });
    } finally {
      close(clientChannel, server);
    }
  }

  @Test
  public void testServerUnrecoverableError() throws InterruptedException {
    final ResponseChannelConsumer consumer = buffer -> Assert.fail("No messages are expected");

    final Closeable server = RSocketServer.create()
                              .payloadDecoder(PayloadDecoder.ZERO_COPY)
                              .acceptor((connectionSetupPayload, rSocket) -> Mono.error(new RuntimeException("Channel could not be created")))
                              .bind(this.serverTransport)
                              .subscribeOn(Schedulers.single())
                              .block();

    Assert.assertNotNull("Server failed to start", server);
    final Address address = buildAddress(0);

    RSocketClientChannel clientChannel = null;
    try {
      clientChannel = buildClientChannel(consumer, address);

      for (int i = 0; i < 10; i++) {
        request(clientChannel, UUID.randomUUID()
                                   .toString());
      }
      //all messages should be dropped
    } finally {
      close(clientChannel, server);
    }
  }

  private void close(final RSocketClientChannel clientChannel, final Closeable server) throws InterruptedException {
    if (clientChannel != null) {
      clientChannel.close();
    }
    if (server != null) {
      try {
        server.dispose();
      } catch (final Throwable t) {
        //ignore
      }
    }
  }

  private void request(final RSocketClientChannel clientChannel, final String request) {
    clientChannel.requestWith(ByteBuffer.wrap(request.getBytes()));
  }

  private Address buildAddress(final int port) {
    return Address.from(Host.of("127.0.0.1"), port, AddressType.NONE);
  }

  private RSocketClientChannel buildClientChannel(final ResponseChannelConsumer consumer, final Address address) {
    return new RSocketClientChannel(this.clientTransport, address, consumer, 100, 1024, LOGGER, Duration.ofMillis(100));
  }

  private final AtomicInteger count = new AtomicInteger(0);
  private final List<String> payloads = new CopyOnWriteArrayList<>();

  @SuppressWarnings("unused")
  private AccessSafely expected(final int total) {
    final AccessSafely access = AccessSafely.afterCompleting(total);

    access.writingWith("messages", (Payload p) -> payloads.add(p.getDataUtf8()));
    access.writingWith("textMessages", (String text) -> payloads.add(text));
    access.readingWith("messages", () -> payloads);
    access.readingWith("message", (Integer index) -> payloads.get(index));
    access.readingWith("messagesCount", () -> payloads.size());

    access.writingWith("count", (Integer dummy) -> count.incrementAndGet());
    access.readingWith("count", () -> count.get());

    return access;
  }
}