package engine.network.server;

import engine.Game;
import engine.level.elements.ILevel;
import engine.level.loader.DungeonLoader;
import engine.network.delta.SnapshotHistory;
import engine.network.messages.s2c.SnapshotMessage;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.mockito.MockedStatic;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

public class AuthoritativeServerLoopTest {

  private AuthoritativeServerLoop serverLoop;

  @BeforeEach
  public void setUp() {
    ServerTransport serverTransport = mock(ServerTransport.class);
    serverLoop = new AuthoritativeServerLoop(serverTransport);
    serverLoop.start();
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

  @Test
  void keepClientBaselineAndSnapshotsAtUnchangedLevel() throws ReflectiveOperationException {
    ClientState firstClient = mock(ClientState.class);
    ClientState secondClient = mock(ClientState.class);
    Set<ClientState> clients = Set.of(firstClient, secondClient);
    ILevel level = mock(ILevel.class);
    Field historyField = AuthoritativeServerLoop.class.getDeclaredField("snapshotHistory");
    historyField.setAccessible(true);
    SnapshotHistory history = (SnapshotHistory) historyField.get(serverLoop);
    SnapshotMessage firstSnapshot = new SnapshotMessage(10, List.of(), null);
    SnapshotMessage secondSnapshot = new SnapshotMessage(20, List.of(), null);

    try (MockedStatic<DungeonLoader> dungeonLoader = mockStatic(DungeonLoader.class)) {
      try (MockedStatic<Game> game = mockStatic(Game.class)) {
        dungeonLoader.when(DungeonLoader::currentLevel).thenReturn("test-level");
        game.when(Game::currentLevel).thenReturn(Optional.of(level));

        serverLoop.clearSnapshotBaselinesOnLevelChange(clients);
        history.add(firstSnapshot);
        history.add(secondSnapshot);

        serverLoop.clearSnapshotBaselinesOnLevelChange(clients);

        verify(firstClient, never()).clearSnapshotBaseline(any());
        verify(secondClient, never()).clearSnapshotBaseline(any());
        assertEquals(2, history.size());
        assertEquals(Optional.of(firstSnapshot), history.snapshot(10));
        assertEquals(Optional.of(secondSnapshot), history.snapshot(20));
      }
    }
  }

// TODO: Verify that each subsequent level change clears the snapshot history
//  and resets the baselines of all connected clients. (look: resources.md)
// TODO: Verify that a new level object is detected as a level change, even if its name is unchanged.
}
