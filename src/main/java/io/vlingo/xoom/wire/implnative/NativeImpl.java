package io.vlingo.xoom.wire.implnative;

import io.vlingo.xoom.actors.Logger;
import io.vlingo.xoom.wire.channel.RefreshableSelector;
import io.vlingo.xoom.wire.node.Node;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;

public final class NativeImpl {
  @CEntryPoint(name = "Java_io_vlingo_xoom_wirenative_Native_with")
  public static int with(@CEntryPoint.IsolateThreadContext long isolateId, CCharPointer name) {
    final String nameString = CTypeConversion.toJavaString(name);
    RefreshableSelector.withNoThreshold(Logger.basicLogger());
    Node.with(Node.NO_NODE.id(), Node.NO_NODE.name(), Node.NO_NODE.applicationAddress().host(),
        Node.NO_NODE.applicationAddress().NO_PORT,
        Node.NO_NODE.applicationAddress().NO_PORT);
    return 0;
  }
}