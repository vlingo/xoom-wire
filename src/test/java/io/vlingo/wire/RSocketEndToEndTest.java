package io.vlingo.wire;

import io.vlingo.actors.Stage;
import io.vlingo.actors.World;
import io.vlingo.common.pool.ElasticResourcePool;
import io.vlingo.wire.fdx.inbound.InboundStream;
import io.vlingo.wire.fdx.inbound.InboundStreamInterest;
import io.vlingo.wire.fdx.inbound.rsocket.RSocketInboundChannelReaderProvider;
import io.vlingo.wire.fdx.outbound.ApplicationOutboundStream;
import io.vlingo.wire.fdx.outbound.rsocket.ManagedOutboundRSocketChannelProvider;
import io.vlingo.wire.message.ConsumerByteBufferPool;
import io.vlingo.wire.message.RawMessage;
import io.vlingo.wire.node.*;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.Assert.assertTrue;

public class RSocketEndToEndTest extends BaseWireTest {

  @Test
  public void test() throws Exception {
    final World world = World.startWithDefaults("rsocket-integration-test-world");

    final Stage stage = world.stage();
    final Configuration configuration = new MockConfiguration();
    final Node node = configuration.nodeMatching(Id.of(1));

    RSocketInboundChannelReaderProvider channelReaderProvider = new RSocketInboundChannelReaderProvider(
        1024, world.defaultLogger());

    final CountDownLatch latch = new CountDownLatch(1000);

    final InboundStream inboundStream = InboundStream.instance(
        stage,
        channelReaderProvider,
        new Interest(latch),
        node.applicationAddress().port(),
        AddressType.APP,
        "APP",
        7L);

    final ApplicationOutboundStream outboundStream = ApplicationOutboundStream.instance(
        stage,
        new ManagedOutboundRSocketChannelProvider(node, AddressType.APP, configuration),
        new ConsumerByteBufferPool(ElasticResourcePool.Config.of(10), 1024));

    IntStream.range(0, (int)latch.getCount()).forEach(i -> {
      outboundStream.sendTo(
          RawMessage.from(2, -1, "hello world"),
          node.id());
    });
    latch.await();
    outboundStream.conclude();
    inboundStream.conclude();
  }

  static class Interest implements InboundStreamInterest {

    static final Logger logger = LoggerFactory.getLogger(Interest.class);

    final CountDownLatch latch;

    Interest(CountDownLatch latch) {
      this.latch = latch;
    }

    @Override
    public void handleInboundStreamMessage(AddressType addressType, RawMessage message) {
      logger.debug("Received: addressType={}, message={}", addressType, message);
      latch.countDown();
      System.out.println("receiving message: #" + latch.getCount());
    }
  }

}
