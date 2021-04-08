package io.vlingo.xoom.wire.fdx.bidirectional;

import org.junit.Test;

public class SSLTest {

  @Test
  public void testThatBasicSSLWorks() throws Exception {
// THIS IS FOR TESTING BASIC SSL ONLY AND SHOULD NOT NORMALLY RUN. ENDLESS LOOP AT LINE 84.
// COPIED FROM: https://examples.javacodegeeks.com/core-java/nio/java-nio-ssl-example/
//
//    InetSocketAddress address = new InetSocketAddress("google.com", 443);
//    Selector selector = Selector.open();
//    SocketChannel channel = SocketChannel.open();
//    channel.connect(address);
//    channel.configureBlocking(false);
//    int ops = SelectionKey.OP_CONNECT | SelectionKey.OP_READ;
//
//    SelectionKey key =  channel.register(selector, ops);
//
//    // create the worker threads
//    final Executor ioWorker = Executors.newSingleThreadExecutor();
//    final Executor taskWorkers = Executors.newFixedThreadPool(2);
//
//    // create the SSLEngine
//    final SSLEngine engine = SSLContext.getDefault().createSSLEngine();
//    engine.setUseClientMode(true);
//    engine.beginHandshake();
//    final int ioBufferSize = 32 * 1024;
//    final NioSSLProvider ssl = new NioSSLProvider(key, engine, ioBufferSize, ioWorker, taskWorkers)
//    {
//       @Override
//       public void onFailure(Exception ex)
//       {
//          System.out.println("handshake failure");
//          ex.printStackTrace();
//       }
//
//       @Override
//       public void onSuccess()
//       {
//          System.out.println("handshake success");
//          SSLSession session = engine.getSession();
//          try
//          {
//             System.out.println("local principal: " + session.getLocalPrincipal());
//             System.out.println("remote principal: " + session.getPeerPrincipal());
//             System.out.println("cipher: " + session.getCipherSuite());
//          }
//          catch (Exception exc)
//          {
//             exc.printStackTrace();
//          }
//
//          //HTTP request
//          StringBuilder http = new StringBuilder();
//          http.append("GET / HTTP/1.1\r\n");
//          http.append("Connection: close\r\n");
//          http.append("\r\n");
//          byte[] data = http.toString().getBytes();
//          ByteBuffer send = ByteBuffer.wrap(data);
//          this.sendAsync(send);
//       }
//
//       @Override
//       public void onInput(ByteBuffer decrypted)
//       {
//          // HTTP response
//          byte[] dst = new byte[decrypted.remaining()];
//          decrypted.get(dst);
//          String response = new String(dst);
//          System.out.print(response);
//          System.out.flush();
//       }
//
//       @Override
//       public void onClosed()
//       {
//          System.out.println("ssl session closed");
//       }
//    };
//
//    // NIO selector
//    while (true)
//    {
//       key.selector().select();
//       Iterator<SelectionKey> keys = key.selector().selectedKeys().iterator();
//       while (keys.hasNext())
//       {
//          keys.next();
//          keys.remove();
//          ssl.processInput();
//       }
//    }
//  }
//
//  public static abstract class NioSSLProvider extends SSLProvider
//  {
//     private final ByteBuffer buffer = ByteBuffer.allocate(32 * 1024);
//     private final SelectionKey key;
//
//     public NioSSLProvider(SelectionKey key, SSLEngine engine, int bufferSize, Executor ioWorker, Executor taskWorkers)
//     {
//        super(engine, bufferSize, ioWorker, taskWorkers);
//        this.key = key;
//     }
//
//     @Override
//     public void onOutput(ByteBuffer encrypted)
//     {
//        try
//        {
//           ((WritableByteChannel) this.key.channel()).write(encrypted);
//        }
//        catch (IOException exc)
//        {
//           throw new IllegalStateException(exc);
//        }
//     }
//
//     public boolean processInput()
//     {
//      buffer.clear();
//        int bytes;
//        try
//        {
//           bytes = ((ReadableByteChannel) this.key.channel()).read(buffer);
//        }
//        catch (IOException ex)
//        {
//           bytes = -1;
//        }
//        if (bytes == -1) {
//           return false;
//        }
//        buffer.flip();
//        ByteBuffer copy = ByteBuffer.allocate(bytes);
//        copy.put(buffer);
//        copy.flip();
//        this.notify(copy);
//        return true;
//     }
//  }
//
//  public static abstract class SSLProvider implements Runnable
//  {
//     final SSLEngine engine;
//     final Executor ioWorker, taskWorkers;
//     final ByteBuffer clientWrap, clientUnwrap;
//     final ByteBuffer serverWrap, serverUnwrap;
//
//     public SSLProvider(SSLEngine engine, int capacity, Executor ioWorker, Executor taskWorkers)
//     {
//        this.clientWrap = ByteBuffer.allocate(capacity);
//        this.serverWrap = ByteBuffer.allocate(capacity);
//        this.clientUnwrap = ByteBuffer.allocate(capacity);
//        this.serverUnwrap = ByteBuffer.allocate(capacity);
//        this.clientUnwrap.limit(0);
//        this.engine = engine;
//        this.ioWorker = ioWorker;
//        this.taskWorkers = taskWorkers;
//        this.ioWorker.execute(this);
//     }
//
//     public abstract void onInput(ByteBuffer decrypted);
//     public abstract void onOutput(ByteBuffer encrypted);
//     public abstract void onFailure(Exception ex);
//     public abstract void onSuccess();
//     public abstract void onClosed();
//
//     public void sendAsync(final ByteBuffer data)
//     {
//        this.ioWorker.execute(new Runnable()
//        {
//           @Override
//           public void run()
//           {
//              clientWrap.put(data);
//
//              SSLProvider.this.run();
//           }
//        });
//     }
//
//     public void notify(final ByteBuffer data)
//     {
//        this.ioWorker.execute(new Runnable()
//        {
//           @Override
//           public void run()
//           {
//              clientUnwrap.put(data);
//              SSLProvider.this.run();
//           }
//        });
//     }
//
//     @Override
//    public void run()
//     {
//        // executes non-blocking tasks on the IO-Worker
//        while (this.isHandShaking())
//        {
//           continue;
//        }
//     }
//
//     private synchronized boolean isHandShaking()
//     {
//        switch (engine.getHandshakeStatus())
//        {
//           case NOT_HANDSHAKING:
//              boolean occupied = false;
//              {
//                 if (clientWrap.position() > 0)
//                   occupied |= this.wrap();
//                 if (clientUnwrap.position() > 0)
//                   occupied |= this.unwrap();
//              }
//              return occupied;
//
//           case NEED_WRAP:
//              if (!this.wrap())
//                 return false;
//              break;
//
//           case NEED_UNWRAP:
//              if (!this.unwrap())
//                 return false;
//              break;
//
//           case NEED_TASK:
//              final Runnable sslTask = engine.getDelegatedTask();
//              if (sslTask == null) return false;
//              Runnable wrappedTask = new Runnable()
//              {
//                 @Override
//                 public void run()
//                 {
//                    sslTask.run();
//                    ioWorker.execute(SSLProvider.this);
//                 }
//              };
//              taskWorkers.execute(wrappedTask);
//              return false;
//
//           case FINISHED:
//              throw new IllegalStateException("FINISHED");
//        }
//
//        return true;
//     }
//
//     private boolean wrap()
//     {
//        SSLEngineResult wrapResult;
//
//        try
//        {
//           clientWrap.flip();
//           wrapResult = engine.wrap(clientWrap, serverWrap);
//           clientWrap.compact();
//        }
//        catch (SSLException exc)
//        {
//           this.onFailure(exc);
//           return false;
//        }
//
//        switch (wrapResult.getStatus())
//        {
//           case OK:
//              if (serverWrap.position() > 0)
//              {
//                 serverWrap.flip();
//                 this.onOutput(serverWrap);
//                 serverWrap.compact();
//              }
//              break;
//
//           case BUFFER_UNDERFLOW:
//              // try again later
//              break;
//
//           case BUFFER_OVERFLOW:
//              throw new IllegalStateException("failed to wrap");
//
//           case CLOSED:
//              this.onClosed();
//              return false;
//        }
//
//        return true;
//     }
//
//     private boolean unwrap()
//     {
//        SSLEngineResult unwrapResult;
//
//        try
//        {
//           clientUnwrap.flip();
//           unwrapResult = engine.unwrap(clientUnwrap, serverUnwrap);
//           clientUnwrap.compact();
//        }
//        catch (SSLException ex)
//        {
//           this.onFailure(ex);
//           return false;
//        }
//
//        switch (unwrapResult.getStatus())
//        {
//           case OK:
//              if (serverUnwrap.position() > 0)
//              {
//                 serverUnwrap.flip();
//                 this.onInput(serverUnwrap);
//                 serverUnwrap.compact();
//              }
//              break;
//
//           case CLOSED:
//              this.onClosed();
//              return false;
//
//           case BUFFER_OVERFLOW:
//              throw new IllegalStateException("failed to unwrap");
//
//           case BUFFER_UNDERFLOW:
//              return false;
//        }
//
//        if (unwrapResult.getHandshakeStatus() == HandshakeStatus.FINISHED)
//        {
//              this.onSuccess();
//              return false;
//        }
//
//        return true;
//     }
  }
}
