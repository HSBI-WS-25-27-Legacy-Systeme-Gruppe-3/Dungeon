package engine.network.server;

import engine.Game;
import engine.level.elements.ILevel;
import engine.level.loader.DungeonLoader;
import java.util.Optional;
import java.util.Set;
import org.mockito.MockedStatic;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

public class AuthoritativeServerLoopTest {

  private ServerTransport serverTransport;
  private AuthoritativeServerLoop serverLoop;

  @BeforeEach
  public void setUp() {
    serverTransport = mock(ServerTransport.class);
    serverLoop = new AuthoritativeServerLoop(serverTransport);
    serverLoop.start();
  }

  @Test
  void dontClearBaselineAtFirstLevel() {
    ClientState client = mock(ClientState.class);
    ILevel level = mock(ILevel.class);

    try (MockedStatic<DungeonLoader> dungeonLoader = mockStatic(DungeonLoader.class);
        MockedStatic<Game> game = mockStatic(Game.class)) {
      dungeonLoader.when(DungeonLoader::currentLevel).thenReturn("test-level");
      game.when(Game::currentLevel).thenReturn(Optional.of(level));

      serverLoop.clearSnapshotBaselinesOnLevelChange(Set.of(client));

      verify(client, never()).clearSnapshotBaseline(any());
    }
  }

  @AfterEach
  void tearDown() {
    serverLoop.stop();
  }

  @Test
  void stopShutsDownExecutor() {
    assertTrue(serverLoop.isRunning());
    serverLoop.stop();
    assertFalse(serverLoop.isRunning());
  }





// TODO: Verify that each subsequent level change clears the snapshot history
//  and resets the baselines of all connected clients. (look: resources.md)

// TODO: Verify that client baselines and snapshot history are not reset when the level is unchanged.

// TODO: Verify that a new level object is detected as a level change, even if its name is unchanged.
}
