package io.vlingo.wire.fdx.outbound.rsocket;

import io.rsocket.AbstractRSocket;
import io.rsocket.Closeable;
import io.rsocket.ConnectionSetupPayload;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.RSocketFactory;
import io.rsocket.SocketAcceptor;
import io.rsocket.transport.ClientTransport;
import io.rsocket.transport.ServerTransport;
import io.rsocket.transport.netty.client.TcpClientTransport;
import io.rsocket.transport.netty.server.CloseableChannel;
import io.rsocket.transport.netty.server.TcpServerTransport;
import io.vlingo.actors.Logger;
import io.vlingo.wire.message.ByteBufferAllocator;
import io.vlingo.wire.message.RawMessage;
import io.vlingo.wire.node.Address;
import io.vlingo.wire.node.AddressType;
import io.vlingo.wire.node.Host;
import org.junit.Assert;
import org.junit.Test;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

public class RSocketOutboundChannelTest {
	private static final Logger logger = Logger.basicLogger();

	@Test
	public void testChannelReconnects() throws InterruptedException {
		final ClientTransport localClientTransport = TcpClientTransport.create(49000);
		final ServerTransport<CloseableChannel> serverTransport = TcpServerTransport.create(49000);

		CountDownLatch firstCdl = new CountDownLatch(1);
		final Closeable serverSocket = createServerSocket(serverTransport, "node1", firstCdl);

		final RSocketOutboundChannel outbound = new RSocketOutboundChannel(new Address(Host.of("127.0.0.1"), 0, AddressType.MAIN),
				localClientTransport, Duration.ofMillis(200), logger);

		final String message = UUID.randomUUID().toString();

		final RawMessage rawMessage = RawMessage.from(0, 0, message);
		final ByteBuffer buffer = ByteBufferAllocator.allocate(1024);

		outbound.write(rawMessage.asByteBuffer(buffer));
		Assert.assertEquals(1, firstCdl.getCount());

		serverSocket.dispose();

		Thread.sleep(200);

		outbound.write(rawMessage.asByteBuffer(buffer));

		final CountDownLatch secondCdl = new CountDownLatch(1);

		final Closeable secondServerSocket = createServerSocket(serverTransport, "node2", secondCdl);

		outbound.write(rawMessage.asByteBuffer(buffer));

		Assert.assertEquals(1, secondCdl.getCount());
		Assert.assertEquals(0, firstCdl.getCount());
	}

	private Closeable createServerSocket(ServerTransport<CloseableChannel> serverTransport, String name, CountDownLatch countDownLatch) {
		final Closeable serverSocket = RSocketFactory.receive()
				.acceptor(new SocketAcceptor() {
					@Override
					public Mono<RSocket> accept(ConnectionSetupPayload connectionSetupPayload, RSocket rSocket) {
						return Mono.just(new AbstractRSocket() {
							@Override
							public Mono<Void> fireAndForget(Payload payload) {
								logger.debug("Server socket {} received message", name);
								countDownLatch.countDown();
								return Mono.empty();
							}
						});
					}
				})
				.transport(serverTransport)
				.start()
				.block();

		serverSocket.onClose().doFinally(signalType -> {
			logger.debug("Server socket closed");
		});

		return serverSocket;
	}
}