package io.vlingo.wire.fdx.integration;

import io.rsocket.transport.local.LocalClientTransport;
import io.rsocket.transport.local.LocalServerTransport;
import io.vlingo.actors.Stage;
import io.vlingo.actors.World;
import io.vlingo.common.pool.ElasticResourcePool;
import io.vlingo.wire.BaseWireTest;
import io.vlingo.wire.fdx.inbound.InboundStream;
import io.vlingo.wire.fdx.inbound.InboundStreamInterest;
import io.vlingo.wire.fdx.inbound.rsocket.RSocketInboundChannelReaderProvider;
import io.vlingo.wire.fdx.outbound.ApplicationOutboundStream;
import io.vlingo.wire.fdx.outbound.rsocket.ManagedOutboundRSocketChannelProvider;
import io.vlingo.wire.message.ConsumerByteBufferPool;
import io.vlingo.wire.message.RawMessage;
import io.vlingo.wire.node.*;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.Assert.assertTrue;

public class InboundOutboundIntegrationTests extends BaseWireTest {

  @Test
  public void testRSocket() throws Exception {
    final World world = World.startWithDefaults("rsocket-integration-test-world");

    final Stage stage = world.stage();
    final Configuration configuration = new MockConfiguration();
    final Node node = configuration.nodeMatching(Id.of(1));

    final LocalServerTransport serverTransport = LocalServerTransport.createEphemeral();

    RSocketInboundChannelReaderProvider channelReaderProvider = new RSocketInboundChannelReaderProvider(
        1024, world.defaultLogger(), port -> serverTransport);

    final int nrMessages = 1000;
    final CountDownLatch latch = new CountDownLatch(nrMessages);

    final InboundStream inboundStream = InboundStream.instance(
        stage,
        channelReaderProvider,
        new Interest(latch),
        node.applicationAddress().port(),
        AddressType.APP,
        "APP",
        7L);

    //The Inbound stream initialization is asynchronous,
    // so wee need to wait a bit for the RSocketChannelInboundReader to initialize.
    Thread.sleep(1000);


    final ApplicationOutboundStream outboundStream = ApplicationOutboundStream.instance(
        stage,
        new ManagedOutboundRSocketChannelProvider(node, AddressType.APP, configuration, Duration.ofMillis(1000), address -> serverTransport.clientTransport()),
        new ConsumerByteBufferPool(ElasticResourcePool.Config.of(10), 1024));

    try {
      IntStream.range(0, nrMessages).forEach(i -> {
        outboundStream.sendTo(
                RawMessage.from(2, -1, "hello world:" + i ),
                node.id());
      });
      Assert.assertTrue(latch.await(5, TimeUnit.SECONDS));
    } finally {
      //Close the streams independently of the sucess of the test
      outboundStream.conclude();
      inboundStream.conclude();
    }
  }

  static class Interest implements InboundStreamInterest {

    static final Logger logger = LoggerFactory.getLogger(Interest.class);

    final CountDownLatch latch;

    Interest(CountDownLatch latch) {
      this.latch = latch;
    }

    @Override
    public void handleInboundStreamMessage(AddressType addressType, RawMessage message) {
      logger.debug("Received: addressType={}, message={}", addressType, message.asTextMessage());
      latch.countDown();
      logger.debug("Left {} msg to receive", latch.getCount());
    }
  }

}
