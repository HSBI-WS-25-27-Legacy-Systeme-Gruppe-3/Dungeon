package engine.network.server;

import engine.Game;
import engine.level.elements.ILevel;
import engine.level.loader.DungeonLoader;
import engine.network.FullSnapshotSendReason;
import engine.network.delta.SnapshotHistory;
import engine.network.messages.s2c.SnapshotMessage;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import engine.resources.Res;
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
    Res<ILevel> res = mock(Res.class);
    when(res.version()).thenReturn(1L, 2L);
    ServerTransport serverTransport = mock(ServerTransport.class);
    serverLoop = new AuthoritativeServerLoop(serverTransport, res);
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

  @Test
  void clearClientBaselinesAndSnapshotsWhenLevelObjectChangesDespiteSameName()
      throws ReflectiveOperationException {
    ClientState firstClient = mock(ClientState.class);
    ClientState secondClient = mock(ClientState.class);
    Set<ClientState> clients = Set.of(firstClient, secondClient);
    ILevel firstLevel = mock(ILevel.class);
    ILevel secondLevel = mock(ILevel.class);
    Field historyField = AuthoritativeServerLoop.class.getDeclaredField("snapshotHistory");
    historyField.setAccessible(true);
    SnapshotHistory history = (SnapshotHistory) historyField.get(serverLoop);

    try (MockedStatic<DungeonLoader> dungeonLoader = mockStatic(DungeonLoader.class)) {
      try (MockedStatic<Game> game = mockStatic(Game.class)) {
        dungeonLoader.when(DungeonLoader::currentLevel).thenReturn("test-level");
        game.when(Game::currentLevel).thenReturn(Optional.of(firstLevel));
        serverLoop.clearSnapshotBaselinesOnLevelChange(clients);
        history.add(new SnapshotMessage(10, List.of(), null));
        history.add(new SnapshotMessage(20, List.of(), null));
        assertEquals(2, history.size());

        game.when(Game::currentLevel).thenReturn(Optional.of(secondLevel));
        serverLoop.clearSnapshotBaselinesOnLevelChange(clients);

        assertEquals(0, history.size());
        assertTrue(history.snapshot(10).isEmpty());
        assertTrue(history.snapshot(20).isEmpty());
        verify(firstClient).clearSnapshotBaseline(FullSnapshotSendReason.LEVEL_CHANGE);
        verify(secondClient).clearSnapshotBaseline(FullSnapshotSendReason.LEVEL_CHANGE);
      }
    }
  }

}
