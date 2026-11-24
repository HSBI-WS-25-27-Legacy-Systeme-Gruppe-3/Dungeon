package engine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.junit.jupiter.api.Assertions.assertThrows;

import engine.Game;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class ServerLifecycleTest {

  @Test
  void repeatedShutdownRequestsAreHandledOnlyOnce() {
    AtomicInteger callbackCount = new AtomicInteger();

    try (MockedStatic<Game> game = mockStatic(Game.class)) {
      ServerLifecycle lifecycle = ServerLifecycle.install("Server stopped", callbackCount::incrementAndGet);

      lifecycle.requestExit("First shutdown");
      lifecycle.requestExit("Second shutdown");

      game.verify(() -> Game.exit("First shutdown"), times(1));
      assertEquals(1, callbackCount.get());
    }
  }

  @Test
  void afterExitRunsEvenWhenShutdownFails() {
    AtomicInteger callbackCount = new AtomicInteger();

    try (MockedStatic<Game> game = mockStatic(Game.class)) {
      game.when(() -> Game.exit("Shutdown"))
          .thenThrow(new IllegalStateException("Shutdown failed"));

      ServerLifecycle lifecycle = ServerLifecycle.install("Server stopped", callbackCount::incrementAndGet);

      assertThrows(
          IllegalStateException.class,
          () -> lifecycle.requestExit("Shutdown"));

      assertEquals(1, callbackCount.get());
    }
  }

  @Test
  void repeatedCompletionRequestsAreHandledOnlyOnce() {
    AtomicInteger callbackCount = new AtomicInteger();

    try (MockedStatic<Game> game = mockStatic(Game.class)) {
      ServerLifecycle lifecycle = ServerLifecycle.install("Server stopped", callbackCount::incrementAndGet);

      lifecycle.requestCompletion();
      lifecycle.requestCompletion();

      game.verify(Game::complete, times(1));
      assertEquals(1, callbackCount.get());
    }
  }

  @Test
  void shutdownAfterCompletionIsIgnored() {
    AtomicInteger callbackCount = new AtomicInteger();

    try (MockedStatic<Game> game = mockStatic(Game.class)) {
      ServerLifecycle lifecycle = ServerLifecycle.install("Server stopped", callbackCount::incrementAndGet);

      lifecycle.requestCompletion();
      lifecycle.requestExit("Shutdown");

      game.verify(Game::complete, times(1));
      game.verify(() -> Game.exit("Shutdown"), times(0));

      assertEquals(1, callbackCount.get());
    }
  }

  @Test
  void completionAfterShutdownIsIgnored() {
    AtomicInteger callbackCount = new AtomicInteger();

    try (MockedStatic<Game> game = mockStatic(Game.class)) {
      ServerLifecycle lifecycle = ServerLifecycle.install("Server stopped", callbackCount::incrementAndGet);

      lifecycle.requestExit("Shutdown");
      lifecycle.requestCompletion();

      game.verify(() -> Game.exit("Shutdown"), times(1));
      game.verify(Game::complete, times(0));

      assertEquals(1, callbackCount.get());
    }
  }

  @Test
  void afterExitRunsEvenWhenCompletionFails() {
    AtomicInteger callbackCount = new AtomicInteger();

    try (MockedStatic<Game> game = mockStatic(Game.class)) {
      game.when(Game::complete)
          .thenThrow(new IllegalStateException("Completion failed"));

      ServerLifecycle lifecycle = ServerLifecycle.install("Server stopped", callbackCount::incrementAndGet);

      assertThrows(
          IllegalStateException.class,
          lifecycle::requestCompletion);

      assertEquals(1, callbackCount.get());

      game.verify(Game::complete, times(1));
    }
  }
}