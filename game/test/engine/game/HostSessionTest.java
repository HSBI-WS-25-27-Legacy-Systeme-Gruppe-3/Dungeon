package engine.game;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

class HostSessionTest {

  @Test
  void noServerIsHostedInitially() {
    HostSession.hosting(null);

    assertFalse(HostSession.isHosting());
    assertFalse(HostSession.isServerRunning());
  }

  @Test
  void hostingRegistersServerProcess() {
    ServerProcess process = mock(ServerProcess.class);

    try {
      HostSession.hosting(process);

      assertTrue(HostSession.isHosting());
    } finally {
      HostSession.hosting(null);
    }
  }

  @Test
  void runningServerIsDetected() {
    ServerProcess process = mock(ServerProcess.class);

    when(process.isAlive()).thenReturn(true);

    try {
      HostSession.hosting(process);

      assertTrue(HostSession.isServerRunning());
      verify(process).isAlive();
    } finally {
      HostSession.hosting(null);
    }
  }

  @Test
  void stoppedServerIsDetected() {
    ServerProcess process = mock(ServerProcess.class);

    when(process.isAlive()).thenReturn(false);

    try {
      HostSession.hosting(process);

      assertTrue(HostSession.isHosting());
      assertFalse(HostSession.isServerRunning());

      verify(process).isAlive();
    } finally {
      HostSession.hosting(null);
    }
  }

  @Test
  void stopHostingAsyncStopsServer() {
    ServerProcess process = mock(ServerProcess.class);

    HostSession.hosting(process);

    try {
      CompletableFuture<Void> shutdown = HostSession.stopHostingAsync();

      shutdown.join();

      verify(process, times(1)).stop();
      assertFalse(HostSession.isHosting());
      assertFalse(HostSession.isServerRunning());
    } finally {
      HostSession.hosting(null);
    }
  }

  @Test
  void repeatedShutdownRequestsStopServerOnlyOnce() {
    ServerProcess process = mock(ServerProcess.class);

    HostSession.hosting(process);

    try {
      CompletableFuture<Void> firstShutdown = HostSession.stopHostingAsync();
      CompletableFuture<Void> secondShutdown = HostSession.stopHostingAsync();

      assertSame(firstShutdown, secondShutdown);

      firstShutdown.join();

      verify(process, times(1)).stop();
      assertFalse(HostSession.isHosting());
    } finally {
      HostSession.hosting(null);
    }
  }

  @Test
  void shutdownFailureIsPropagated() {
    ServerProcess process = mock(ServerProcess.class);

    doThrow(new IllegalStateException("Shutdown failed")).when(process).stop();

    HostSession.hosting(process);

    try {
      CompletableFuture<Void> shutdown = HostSession.stopHostingAsync();

      CompletionException exception = assertThrows(CompletionException.class, shutdown::join);

      assertInstanceOf(IllegalStateException.class, exception.getCause());

      verify(process, times(1)).stop();
      assertFalse(HostSession.isHosting());
    } finally {
      HostSession.hosting(null);
    }
  }

  @Test
  void stopHostingWaitsForServerShutdown() {
    ServerProcess process = mock(ServerProcess.class);

    HostSession.hosting(process);

    try {
      HostSession.stopHosting();

      verify(process, times(1)).stop();
      assertFalse(HostSession.isHosting());
      assertFalse(HostSession.isServerRunning());
    } finally {
      HostSession.hosting(null);
    }
  }
}
