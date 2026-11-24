package engine.game;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.doThrow;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockConstruction;

import org.mockito.MockedConstruction;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.io.OutputStream;

class ServerProcessTest {

  private ServerProcess createServerProcess(
      Process process, ServerSocket statusServer) throws Exception {

    when(statusServer.accept())
        .thenThrow(new IOException("Simulated closed status socket"));

    Constructor<ServerProcess> constructor = ServerProcess.class.getDeclaredConstructor(
        Process.class, ServerSocket.class, Consumer.class);
    constructor.setAccessible(true);

    return constructor.newInstance(
        process, statusServer, (Consumer<String>) status -> {
        });
  }

  @Test
  void runningProcessIsDetected() throws Exception {
    Process process = mock(Process.class);
    ServerSocket statusServer = mock(ServerSocket.class);

    when(process.isAlive()).thenReturn(true);

    ServerProcess server = createServerProcess(process, statusServer);

    assertTrue(server.isAlive());
  }

  @Test
  void stoppedProcessIsDetected() throws Exception {
    Process process = mock(Process.class);
    ServerSocket statusServer = mock(ServerSocket.class);

    when(process.isAlive()).thenReturn(false);

    ServerProcess server = createServerProcess(process, statusServer);

    assertFalse(server.isAlive());
  }

  @Test
  void awaitReadyReturnsFalseWhenProcessHasExited() throws Exception {
    Process process = mock(Process.class);
    ServerSocket statusServer = mock(ServerSocket.class);

    when(process.isAlive()).thenReturn(false);

    ServerProcess server = createServerProcess(process, statusServer);

    assertFalse(server.awaitReady(12345, Duration.ofSeconds(5)));
  }

  @Test
  void awaitReadyReturnsFalseWhenTimeoutExpires() throws Exception {
    Process process = mock(Process.class);
    ServerSocket statusServer = mock(ServerSocket.class);

    when(process.isAlive()).thenReturn(true);

    ServerProcess server = createServerProcess(process, statusServer);

    assertFalse(server.awaitReady(12345, Duration.ZERO));
  }

  @Test
  void stoppingExitedProcessClosesStatusServer() throws Exception {
    Process process = mock(Process.class);
    ServerSocket statusServer = mock(ServerSocket.class);

    when(process.isAlive()).thenReturn(false);

    ServerProcess server = createServerProcess(process, statusServer);

    server.stop();

    verify(process, never()).getOutputStream();
    verify(process, never()).destroy();
    verify(statusServer, atLeastOnce()).close();
  }

  @Test
  void runningProcessReceivesGracefulStopRequest() throws Exception {
    Process process = mock(Process.class);
    ServerSocket statusServer = mock(ServerSocket.class);
    ByteArrayOutputStream childInput = new ByteArrayOutputStream();

    when(process.isAlive()).thenReturn(true);
    when(process.getOutputStream()).thenReturn(childInput);
    when(process.waitFor(15, TimeUnit.SECONDS)).thenReturn(true);

    ServerProcess server = createServerProcess(process, statusServer);

    server.stop();

    assertArrayEquals(new byte[] { 0 }, childInput.toByteArray());
    verify(process).waitFor(15, TimeUnit.SECONDS);
    verify(process, never()).destroy();
    verify(statusServer, atLeastOnce()).close();
  }

  @Test
  void unresponsiveProcessIsDestroyed() throws Exception {
    Process process = mock(Process.class);
    ServerSocket statusServer = mock(ServerSocket.class);
    ByteArrayOutputStream childInput = new ByteArrayOutputStream();

    when(process.isAlive()).thenReturn(true);
    when(process.getOutputStream()).thenReturn(childInput);
    when(process.waitFor(15, TimeUnit.SECONDS))
        .thenReturn(false, true);

    ServerProcess server = createServerProcess(process, statusServer);

    server.stop();

    assertArrayEquals(new byte[] { 0 }, childInput.toByteArray());
    verify(process).destroy();
    verify(process, times(2)).waitFor(15, TimeUnit.SECONDS);
    verify(statusServer, atLeastOnce()).close();
  }

  @Test
  void interruptedShutdownDestroysProcess() throws Exception {
    Process process = mock(Process.class);
    ServerSocket statusServer = mock(ServerSocket.class);
    ByteArrayOutputStream childInput = new ByteArrayOutputStream();

    when(process.isAlive()).thenReturn(true);
    when(process.getOutputStream()).thenReturn(childInput);
    when(process.waitFor(15, TimeUnit.SECONDS))
        .thenThrow(new InterruptedException("Interrupted"));

    ServerProcess server = createServerProcess(process, statusServer);

    try {
      server.stop();

      assertTrue(Thread.currentThread().isInterrupted());
      verify(process).destroy();
      verify(statusServer, atLeastOnce()).close();
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void shutdownContinuesWhenWritingStopSignalFails() throws Exception {
    Process process = mock(Process.class);
    ServerSocket statusServer = mock(ServerSocket.class);
    OutputStream childInput = mock(OutputStream.class);

    when(process.isAlive()).thenReturn(true);
    when(process.getOutputStream()).thenReturn(childInput);
    doThrow(new IOException("Cannot write shutdown signal"))
        .when(childInput).write(0);
    when(process.waitFor(15, TimeUnit.SECONDS)).thenReturn(true);

    ServerProcess server = createServerProcess(process, statusServer);

    server.stop();

    verify(childInput).write(0);
    verify(process).waitFor(15, TimeUnit.SECONDS);
    verify(process, never()).destroy();
    verify(statusServer, atLeastOnce()).close();
  }

  @Test
  void shutdownHandlesStatusSocketCloseFailure() throws Exception {
    Process process = mock(Process.class);
    ServerSocket statusServer = mock(ServerSocket.class);

    when(process.isAlive()).thenReturn(false);

    ServerProcess server = createServerProcess(process, statusServer);

    doThrow(new IOException("Cannot close status socket"))
        .when(statusServer).close();

    assertDoesNotThrow(server::stop);

    verify(statusServer, atLeastOnce()).close();
    verify(process, never()).destroy();
  }

  @Test
  void startRejectsNullEnvironmentOverrides() {
    assertThrows(
        NullPointerException.class,
        () -> ServerProcess.start(
            ServerProcessTest.class,
            12345,
            null,
            status -> {
            },
            ServerProcess.SERVER_ARGUMENT));
  }

  @Test
  void startRejectsNullStatusConsumer() {
    assertThrows(
        NullPointerException.class,
        () -> ServerProcess.start(
            ServerProcessTest.class,
            12345,
            Map.of(),
            null,
            ServerProcess.SERVER_ARGUMENT));
  }

  @Test
  void startRejectsNullEnvironmentValue() {
    Map<String, String> environment = new HashMap<>();
    environment.put("TEST_VARIABLE", null);

    assertThrows(
        NullPointerException.class,
        () -> ServerProcess.start(
            ServerProcessTest.class,
            12345,
            environment,
            status -> {
            },
            ServerProcess.SERVER_ARGUMENT));
  }

  @Test
  void startClosesStatusSocketWhenProcessLaunchFails() throws Exception {
    try (MockedConstruction<ServerSocket> sockets = mockConstruction(ServerSocket.class);
        MockedConstruction<ProcessBuilder> builders = mockConstruction(
            ProcessBuilder.class,
            (builder, context) -> {
              when(builder.redirectOutput(any(ProcessBuilder.Redirect.class)))
                  .thenReturn(builder);
              when(builder.redirectError(any(ProcessBuilder.Redirect.class)))
                  .thenReturn(builder);
              when(builder.environment()).thenReturn(new HashMap<>());
              when(builder.start())
                  .thenThrow(new IOException("Simulated process start failure"));
            })) {

      assertThrows(
          IOException.class,
          () -> ServerProcess.start(
              ServerProcessTest.class,
              12345,
              Map.of(),
              status -> {
              },
              ServerProcess.SERVER_ARGUMENT));

      assertEquals(1, sockets.constructed().size());
      verify(sockets.constructed().get(0)).close();
    }
  }

  @Test
  void buildCommandContainsServerProperties() throws Exception {
    Method method = ServerProcess.class.getDeclaredMethod(
        "buildCommand", Class.class, int.class, int.class, String[].class);
    method.setAccessible(true);

    @SuppressWarnings("unchecked")
    List<String> command = (List<String>) method.invoke(
        null,
        ServerProcessTest.class,
        12345,
        54321,
        new String[] { ServerProcess.SERVER_ARGUMENT });

    assertTrue(command.contains("-D" + ServerProcess.PORT_PROPERTY + "=12345"));
    assertTrue(command.contains("-D" + ServerProcess.MANAGED_PROPERTY + "=true"));
    assertTrue(command.contains("-D" + ServerProcess.STATUS_PORT_PROPERTY + "=54321"));
    assertTrue(command.contains(ServerProcessTest.class.getName()));
    assertTrue(command.contains(ServerProcess.SERVER_ARGUMENT));
  }

  @Test
  void buildCommandPreservesAdditionalArguments() throws Exception {
    Method method = ServerProcess.class.getDeclaredMethod(
        "buildCommand", Class.class, int.class, int.class, String[].class);
    method.setAccessible(true);

    @SuppressWarnings("unchecked")
    List<String> command = (List<String>) method.invoke(
        null,
        ServerProcessTest.class,
        12345,
        54321,
        new String[] { "--server", "--test-mode", "example" });

    int mainClassIndex = command.indexOf(ServerProcessTest.class.getName());

    assertTrue(mainClassIndex >= 0);
    assertEquals(
        List.of("--server", "--test-mode", "example"),
        command.subList(mainClassIndex + 1, command.size()));
  }

  @Test
  void buildCommandHandlesNullArguments() throws Exception {
    Method method = ServerProcess.class.getDeclaredMethod(
        "buildCommand", Class.class, int.class, int.class, String[].class);
    method.setAccessible(true);

    @SuppressWarnings("unchecked")
    List<String> command = (List<String>) method.invoke(
        null,
        ServerProcessTest.class,
        12345,
        54321,
        (String[]) null);

    assertEquals(
        ServerProcessTest.class.getName(),
        command.get(command.size() - 1));
  }
}