package nativebuild;

import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;

import io.vlingo.xoom.actors.Logger;
import io.vlingo.xoom.wire.channel.RefreshableSelector;
import io.vlingo.xoom.wire.node.Address;
import io.vlingo.xoom.wire.node.Node;

public final class NativeBuildEntryPoint {
  @SuppressWarnings("unused")
  @CEntryPoint(name = "Java_io_vlingo_xoom_wirenative_Native_with")
  public static int with(@CEntryPoint.IsolateThreadContext long isolateId, CCharPointer name) {
    final String nameString = CTypeConversion.toJavaString(name);
    RefreshableSelector.withNoThreshold(Logger.basicLogger());
    Node.with(Node.NO_NODE.id(), Node.NO_NODE.name(), Node.NO_NODE.applicationAddress().host(), Address.NO_PORT, Address.NO_PORT);
    return 0;
  }
}