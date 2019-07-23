// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.
package io.vlingo.wire.fdx.bidirectional.rsocket;

import io.rsocket.AbstractRSocket;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.RSocketFactory;
import io.rsocket.frame.decoder.PayloadDecoder;
import io.rsocket.transport.netty.server.TcpServerTransport;
import io.rsocket.util.DefaultPayload;
import io.vlingo.actors.Logger;
import io.vlingo.wire.channel.ResponseChannelConsumer;
import io.vlingo.wire.node.Address;
import io.vlingo.wire.node.AddressType;
import io.vlingo.wire.node.Host;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.reactivestreams.Publisher;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class RSocketClientChannelTest {
  private static final int PORT = 37380;
  private static final Logger LOGGER = Logger.basicLogger();
  private RSocketClientChannel clientChannel;
  private Disposable server;

  @Test
  public void testServerNotAvailable() {
    final ResponseChannelConsumer consumer = buffer -> Assert.fail("No messages are expected");

    final Address address = Address.from(Host.of("localhost"), PORT, AddressType.NONE);

    clientChannel = new RSocketClientChannel(address, consumer, 100, 1024, LOGGER);

    request(clientChannel, UUID.randomUUID().toString());
  }

  @Test
  public void testServerDoesNotReply() throws InterruptedException {
    final ResponseChannelConsumer consumer = buffer -> Assert.fail("No messages are expected");

    final Address address = Address.from(Host.of("localhost"), PORT, AddressType.NONE);

    final CountDownLatch countDownLatch = new CountDownLatch(1);
    final CountDownLatch serverReceivedMessages = new CountDownLatch(100);
    server = RSocketFactory.receive().frameDecoder(PayloadDecoder.ZERO_COPY).acceptor((connectionSetupPayload, rSocket) -> Mono.just(new AbstractRSocket() {
      @Override
      public Flux<Payload> requestChannel(final Publisher<Payload> payloads) {
        countDownLatch.countDown();
        Flux.from(payloads).subscribe(payload -> serverReceivedMessages.countDown());
        return Flux.empty();
      }
    })).transport(TcpServerTransport.create(address.hostName(), PORT)).start().block();

    Thread.sleep(100);

    clientChannel = new RSocketClientChannel(address, consumer, 1, 1024, LOGGER);

    for (int i = 0; i < 100; i++) {
      request(clientChannel, UUID.randomUUID().toString());
    }

    Assert.assertTrue("Server should have received requestChannel request", countDownLatch.await(2, TimeUnit.SECONDS));
    Assert.assertTrue("Server should have received all messages", serverReceivedMessages.await(4, TimeUnit.SECONDS));
  }

  @Test
  public void testServerError() throws InterruptedException {
    final ResponseChannelConsumer consumer = buffer -> Assert.fail("No messages are expected");

    final Address address = Address.from(Host.of("localhost"), PORT, AddressType.NONE);

    final CountDownLatch countDownLatch = new CountDownLatch(1);
    final CountDownLatch serverReceivedMessages = new CountDownLatch(100);

    final AbstractRSocket responseHandler = new AbstractRSocket() {
      @Override
      public Flux<Payload> requestChannel(final Publisher<Payload> payloads) {
        countDownLatch.countDown();
        Flux.from(payloads).subscribe(payload -> serverReceivedMessages.countDown());

        return Flux.error(new Exception("Random exception"));
      }
    };

    server = RSocketFactory.receive()
                           .frameDecoder(PayloadDecoder.ZERO_COPY)
                           .acceptor((connectionSetupPayload, rSocket) -> Mono.just(responseHandler))
                           .transport(TcpServerTransport.create(address.hostName(), PORT))
                           .start()
                           .block();

    Thread.sleep(100);

    clientChannel = new RSocketClientChannel(address, consumer, 1, 1024, LOGGER);

    for (int i = 0; i < 100; i++) {
      request(clientChannel, UUID.randomUUID().toString());
    }

    Assert.assertTrue("Server should have received requestChannel request", countDownLatch.await(2, TimeUnit.SECONDS));
    Assert.assertTrue("Server should have received all messages", serverReceivedMessages.await(4, TimeUnit.SECONDS));
  }

  @Test
  public void testServerRequestReply() throws InterruptedException {
    final Address address = Address.from(Host.of("localhost"), PORT, AddressType.NONE);

    final CountDownLatch countDownLatch = new CountDownLatch(1);
    final CountDownLatch serverReceivedMessages = new CountDownLatch(100);

    Set<String> serverReceivedMessage = new LinkedHashSet<>();

    final RSocket responseHandler = new AbstractRSocket() {
      @Override
      public Flux<Payload> requestChannel(final Publisher<Payload> payloads) {
        countDownLatch.countDown();
        return Flux.from(payloads).doOnNext(payload -> {
          serverReceivedMessages.countDown();
          serverReceivedMessage.add(payload.getDataUtf8());
          payload.release();
        }).zipWith(Flux.range(1, 100), (payload, integer) -> DefaultPayload.create("Reply " + integer));
      }
    };

    server = RSocketFactory.receive()
                           .frameDecoder(PayloadDecoder.ZERO_COPY)
                           .acceptor((connectionSetupPayload, rSocket) -> Mono.just(responseHandler))
                           .transport(TcpServerTransport.create(address.hostName(), PORT))
                           .start()
                           .subscribe();

    Thread.sleep(100);

    final CountDownLatch clientReceivedMessages = new CountDownLatch(100);

    Set<String> serverReplies = new LinkedHashSet<>();

    final ResponseChannelConsumer consumer = buffer -> {
      clientReceivedMessages.countDown();
      serverReplies.add(new String(buffer.array(), 0, buffer.remaining()));
    };

    this.clientChannel = new RSocketClientChannel(address, consumer, 1, 1024, LOGGER);

    Set<String> clientRequests = new LinkedHashSet<>();
    for (int i = 0; i < 100; i++) {
      final String request = "Request_" + i + "_" + UUID.randomUUID().toString();
      request(clientChannel, request);
      clientRequests.add(request);
    }

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

  @After
  public void tearDown() throws Exception {
    if (this.clientChannel != null) {
      this.clientChannel.close();
    }
    if (this.server != null) {
      this.server.dispose();
    }
    Thread.sleep(200);
  }

  private void request(final RSocketClientChannel clientChannel, final String request) {
    clientChannel.requestWith(ByteBuffer.wrap(request.getBytes()));
  }
}