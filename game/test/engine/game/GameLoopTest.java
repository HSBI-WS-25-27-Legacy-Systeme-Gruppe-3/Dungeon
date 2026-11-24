package engine.game;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mockStatic;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

import engine.network.ConnectionListener;
import engine.utils.IVoidFunction;
import engine.level.loader.LevelParser;
import engine.level.DungeonLevel;
import engine.network.MessageDispatcher;
import engine.network.messages.s2c.LevelChangeEvent;
import engine.network.server.ClientState;
import engine.network.server.Session;
import engine.network.handler.INetworkHandler;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import engine.Game;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class GameLoopTest {

  @Test
  void handshakeDoesNotStartBeforeInitialWorldIsComplete() throws Exception {
    Constructor<GameLoop> constructor = GameLoop.class.getDeclaredConstructor();
    constructor.setAccessible(true);
    GameLoop gameLoop = constructor.newInstance();

    Method method = GameLoop.class.getDeclaredMethod("tryCompleteInitialWorldHandshake");
    method.setAccessible(true);

    try (MockedStatic<Game> game = mockStatic(Game.class)) {
      game.when(Game::isMultiplayerClient).thenReturn(true);

      method.invoke(gameLoop);

      Field readySent = GameLoop.class.getDeclaredField("initialWorldReadySent");
      readySent.setAccessible(true);

      assertFalse(readySent.getBoolean(gameLoop));
    }
  }

  @Test
  void handshakeDoesNotStartWithoutPlayer() throws Exception {
    Constructor<GameLoop> constructor = GameLoop.class.getDeclaredConstructor();
    constructor.setAccessible(true);
    GameLoop gameLoop = constructor.newInstance();

    Field worldComplete = GameLoop.class.getDeclaredField("initialWorldCompleteReceived");
    worldComplete.setAccessible(true);
    worldComplete.setBoolean(gameLoop, true);

    Method method = GameLoop.class.getDeclaredMethod("tryCompleteInitialWorldHandshake");
    method.setAccessible(true);

    try (MockedStatic<Game> game = mockStatic(Game.class)) {
      game.when(Game::isMultiplayerClient).thenReturn(true);
      game.when(Game::player).thenReturn(java.util.Optional.empty());

      method.invoke(gameLoop);

      Field readySent = GameLoop.class.getDeclaredField("initialWorldReadySent");
      readySent.setAccessible(true);

      assertFalse(readySent.getBoolean(gameLoop));
    }
  }

  @Test
  void handshakeDoesNotStartOutsideMultiplayerClient() throws Exception {
    Constructor<GameLoop> constructor = GameLoop.class.getDeclaredConstructor();
    constructor.setAccessible(true);
    GameLoop gameLoop = constructor.newInstance();

    Field worldComplete = GameLoop.class.getDeclaredField("initialWorldCompleteReceived");
    worldComplete.setAccessible(true);
    worldComplete.setBoolean(gameLoop, true);

    Method method = GameLoop.class.getDeclaredMethod("tryCompleteInitialWorldHandshake");
    method.setAccessible(true);

    try (MockedStatic<Game> game = mockStatic(Game.class)) {
      game.when(Game::isMultiplayerClient).thenReturn(false);

      method.invoke(gameLoop);

      Field readySent = GameLoop.class.getDeclaredField("initialWorldReadySent");
      readySent.setAccessible(true);

      assertFalse(readySent.getBoolean(gameLoop));
    }
  }

  @Test
  void handshakeDoesNotStartTwice() throws Exception {
    Constructor<GameLoop> constructor = GameLoop.class.getDeclaredConstructor();
    constructor.setAccessible(true);
    GameLoop gameLoop = constructor.newInstance();

    Field worldComplete = GameLoop.class.getDeclaredField("initialWorldCompleteReceived");
    worldComplete.setAccessible(true);
    worldComplete.setBoolean(gameLoop, true);

    Field readySent = GameLoop.class.getDeclaredField("initialWorldReadySent");
    readySent.setAccessible(true);
    readySent.setBoolean(gameLoop, true);

    Method method = GameLoop.class.getDeclaredMethod("tryCompleteInitialWorldHandshake");
    method.setAccessible(true);

    try (MockedStatic<Game> game = mockStatic(Game.class)) {
      game.when(Game::isMultiplayerClient).thenReturn(true);
      game.when(Game::player).thenReturn(java.util.Optional.of(mock(engine.Entity.class)));

      method.invoke(gameLoop);

      assertTrue(readySent.getBoolean(gameLoop));
      game.verify(Game::network, org.mockito.Mockito.never());
    }
  }

  @Test
  void resetInitialWorldHandshakeClearsAllFlags() throws Exception {
    Constructor<GameLoop> constructor = GameLoop.class.getDeclaredConstructor();
    constructor.setAccessible(true);
    GameLoop gameLoop = constructor.newInstance();

    Field worldComplete = GameLoop.class.getDeclaredField("initialWorldCompleteReceived");
    Field readySent = GameLoop.class.getDeclaredField("initialWorldReadySent");
    Field clientReady = GameLoop.class.getDeclaredField("initialWorldClientReady");

    worldComplete.setAccessible(true);
    readySent.setAccessible(true);
    clientReady.setAccessible(true);

    worldComplete.setBoolean(gameLoop, true);
    readySent.setBoolean(gameLoop, true);
    clientReady.setBoolean(gameLoop, true);

    Method method = GameLoop.class.getDeclaredMethod("resetInitialWorldHandshake");
    method.setAccessible(true);
    method.invoke(gameLoop);

    assertFalse(worldComplete.getBoolean(gameLoop));
    assertFalse(readySent.getBoolean(gameLoop));
    assertFalse(clientReady.getBoolean(gameLoop));
  }

  @Test
  void completedHandshakeIsNotRepeated() throws Exception {
    Constructor<GameLoop> constructor = GameLoop.class.getDeclaredConstructor();
    constructor.setAccessible(true);
    GameLoop gameLoop = constructor.newInstance();

    Field worldComplete = GameLoop.class.getDeclaredField("initialWorldCompleteReceived");
    worldComplete.setAccessible(true);
    worldComplete.setBoolean(gameLoop, true);

    Field clientReady = GameLoop.class.getDeclaredField("initialWorldClientReady");
    clientReady.setAccessible(true);
    clientReady.setBoolean(gameLoop, true);

    Method method = GameLoop.class.getDeclaredMethod("tryCompleteInitialWorldHandshake");
    method.setAccessible(true);

    try (MockedStatic<Game> game = mockStatic(Game.class)) {
      game.when(Game::isMultiplayerClient).thenReturn(true);

      method.invoke(gameLoop);

      assertTrue(clientReady.getBoolean(gameLoop));
      game.verify(Game::network, org.mockito.Mockito.never());
    }
  }

  @Test
  void failedHandshakeAllowsRetry() throws Exception {
    Constructor<GameLoop> constructor = GameLoop.class.getDeclaredConstructor();
    constructor.setAccessible(true);
    GameLoop gameLoop = constructor.newInstance();

    Field worldComplete = GameLoop.class.getDeclaredField("initialWorldCompleteReceived");
    worldComplete.setAccessible(true);
    worldComplete.setBoolean(gameLoop, true);

    Field readySent = GameLoop.class.getDeclaredField("initialWorldReadySent");
    readySent.setAccessible(true);

    Method method = GameLoop.class.getDeclaredMethod("tryCompleteInitialWorldHandshake");
    method.setAccessible(true);

    INetworkHandler network = mock(INetworkHandler.class);

    when(network.send(
        eq((short) 0),
        any(),
        eq(true)))
        .thenReturn(CompletableFuture.completedFuture(false));

    try (MockedStatic<Game> game = mockStatic(Game.class)) {
      game.when(Game::isMultiplayerClient).thenReturn(true);
      game.when(Game::player)
          .thenReturn(Optional.of(mock(engine.Entity.class)));
      game.when(Game::network).thenReturn(network);

      method.invoke(gameLoop);

      assertFalse(readySent.getBoolean(gameLoop));
    }
  }

  @Test
  void successfulHandshakeMarksClientReady() throws Exception {
    Constructor<GameLoop> constructor = GameLoop.class.getDeclaredConstructor();
    constructor.setAccessible(true);
    GameLoop gameLoop = constructor.newInstance();

    Field worldComplete = GameLoop.class.getDeclaredField("initialWorldCompleteReceived");
    worldComplete.setAccessible(true);
    worldComplete.setBoolean(gameLoop, true);

    Field readySent = GameLoop.class.getDeclaredField("initialWorldReadySent");
    readySent.setAccessible(true);

    Field clientReady = GameLoop.class.getDeclaredField("initialWorldClientReady");
    clientReady.setAccessible(true);

    Method method = GameLoop.class.getDeclaredMethod("tryCompleteInitialWorldHandshake");
    method.setAccessible(true);

    INetworkHandler network = mock(INetworkHandler.class);

    when(network.send(eq((short) 0), any(), eq(true)))
        .thenReturn(CompletableFuture.completedFuture(true));

    try (MockedStatic<Game> game = mockStatic(Game.class)) {
      game.when(Game::isMultiplayerClient).thenReturn(true);
      game.when(Game::player)
          .thenReturn(Optional.of(mock(engine.Entity.class)));
      game.when(Game::network).thenReturn(network);

      method.invoke(gameLoop);

      assertTrue(readySent.getBoolean(gameLoop));
      assertTrue(clientReady.getBoolean(gameLoop));

      org.mockito.Mockito.verify(network).markInitialWorldReady();
    }
  }

  @Test
  void disconnectReasonContainsProvidedReason() throws Exception {
    Constructor<GameLoop> constructor = GameLoop.class.getDeclaredConstructor();
    constructor.setAccessible(true);
    GameLoop gameLoop = constructor.newInstance();

    Method method = GameLoop.class.getDeclaredMethod(
        "multiplayerDisconnectReason", String.class);
    method.setAccessible(true);

    String reason = "Connection lost";

    String message = (String) method.invoke(gameLoop, reason);

    assertTrue(message.contains(reason));
  }

  @Test
  void disconnectWithoutReasonReturnsGenericMessage() throws Exception {
    Constructor<GameLoop> constructor = GameLoop.class.getDeclaredConstructor();
    constructor.setAccessible(true);
    GameLoop gameLoop = constructor.newInstance();

    Method method = GameLoop.class.getDeclaredMethod(
        "multiplayerDisconnectReason", String.class);
    method.setAccessible(true);

    String message = (String) method.invoke(gameLoop, (String) null);

    assertNotNull(message);
    assertFalse(message.isBlank());
  }

  @Test
  void levelChangeClearsPreviousNetworkState() throws Exception {
    Constructor<GameLoop> constructor = GameLoop.class.getDeclaredConstructor();
    constructor.setAccessible(true);
    GameLoop gameLoop = constructor.newInstance();

    MessageDispatcher dispatcher = new MessageDispatcher();
    INetworkHandler network = mock(INetworkHandler.class);

    when(network.messageDispatcher()).thenReturn(dispatcher);

    try (MockedStatic<Game> game = mockStatic(Game.class)) {
      game.when(Game::network).thenReturn(network);
      game.when(Game::player).thenReturn(Optional.empty());

      Method setupHandlers = GameLoop.class.getDeclaredMethod("setupMessageHandlers");
      setupHandlers.setAccessible(true);
      setupHandlers.invoke(gameLoop);

      ClientState clientState = mock(ClientState.class);
      Session session = mock(Session.class);

      when(session.clientState()).thenReturn(Optional.of(clientState));

      LevelChangeEvent event = new LevelChangeEvent("test-level", "");

      dispatcher.dispatch(session, event);

      verify(clientState).clearSnapshotBaseline();
      verify(clientState).clearNetworkEntities();
    }
  }

  @Test
  void levelChangeWithoutSessionDoesNotFail() throws Exception {
    Constructor<GameLoop> constructor = GameLoop.class.getDeclaredConstructor();
    constructor.setAccessible(true);
    GameLoop gameLoop = constructor.newInstance();

    MessageDispatcher dispatcher = new MessageDispatcher();
    INetworkHandler network = mock(INetworkHandler.class);
    DungeonLevel level = mock(DungeonLevel.class);

    when(network.messageDispatcher()).thenReturn(dispatcher);

    try (MockedStatic<Game> game = mockStatic(Game.class);
        MockedStatic<LevelParser> levelParser = mockStatic(LevelParser.class)) {

      game.when(Game::network).thenReturn(network);
      game.when(Game::player).thenReturn(Optional.empty());

      levelParser.when(() -> LevelParser.parseLevel("", "test-level"))
          .thenReturn(level);

      Method setupHandlers = GameLoop.class.getDeclaredMethod("setupMessageHandlers");
      setupHandlers.setAccessible(true);
      setupHandlers.invoke(gameLoop);

      LevelChangeEvent event = new LevelChangeEvent("test-level", "");

      dispatcher.dispatch(null, event);

      levelParser.verify(() -> LevelParser.parseLevel("", "test-level"));
      game.verify(() -> Game.currentLevel(level));
      game.verify(Game::player);
    }
  }

  @Test
  void setupDisablesFurtherInitialization() throws Exception {
    Constructor<GameLoop> constructor = GameLoop.class.getDeclaredConstructor();
    constructor.setAccessible(true);
    GameLoop gameLoop = constructor.newInstance();

    Field doSetupField = GameLoop.class.getDeclaredField("doSetup");
    doSetupField.setAccessible(true);
    doSetupField.setBoolean(gameLoop, true);

    Method setup = GameLoop.class.getDeclaredMethod("setup", boolean.class);
    setup.setAccessible(true);

    try (MockedStatic<PreRunConfiguration> configuration = mockStatic(PreRunConfiguration.class);
        MockedStatic<Game> game = mockStatic(Game.class)) {

      configuration.when(PreRunConfiguration::multiplayerEnabled)
          .thenReturn(true);
      game.when(Game::isMultiplayerClient).thenReturn(false);

      // setup() beginnt, wird aber später durch fehlende
      // Initialisierung der Testumgebung unterbrochen.
      try {
        setup.invoke(gameLoop, false);
      } catch (java.lang.reflect.InvocationTargetException ignored) {
        // Für diesen Test ist nur die erste Zustandsänderung relevant.
      }

      assertFalse(doSetupField.getBoolean(gameLoop));
    }
  }

  @Test
  void setupDoesNotStartNetworkWhenDisabled() throws Exception {
    Constructor<GameLoop> constructor = GameLoop.class.getDeclaredConstructor();
    constructor.setAccessible(true);
    GameLoop gameLoop = constructor.newInstance();

    INetworkHandler network = mock(INetworkHandler.class);
    IVoidFunction onSetup = mock(IVoidFunction.class);

    Method setup = GameLoop.class.getDeclaredMethod("setup", boolean.class);
    setup.setAccessible(true);

    try (MockedStatic<PreRunConfiguration> configuration = mockStatic(PreRunConfiguration.class);
        MockedStatic<Game> game = mockStatic(Game.class)) {

      configuration.when(PreRunConfiguration::multiplayerEnabled)
          .thenReturn(true);
      configuration.when(PreRunConfiguration::userOnSetup)
          .thenReturn(onSetup);

      game.when(Game::isMultiplayerClient).thenReturn(false);
      game.when(Game::isHeadless).thenReturn(true);
      game.when(Game::network).thenReturn(network);

      setup.invoke(gameLoop, false);

      verify(onSetup).execute();
      verify(network, never()).start();
    }
  }

  @Test
  void setupStartsNetworkWhenEnabled() throws Exception {
    Constructor<GameLoop> constructor = GameLoop.class.getDeclaredConstructor();
    constructor.setAccessible(true);
    GameLoop gameLoop = constructor.newInstance();

    INetworkHandler network = mock(INetworkHandler.class);
    IVoidFunction onSetup = mock(IVoidFunction.class);

    Method setup = GameLoop.class.getDeclaredMethod("setup", boolean.class);
    setup.setAccessible(true);

    try (MockedStatic<PreRunConfiguration> configuration = mockStatic(PreRunConfiguration.class);
        MockedStatic<Game> game = mockStatic(Game.class)) {

      configuration.when(PreRunConfiguration::multiplayerEnabled)
          .thenReturn(true);
      configuration.when(PreRunConfiguration::userOnSetup)
          .thenReturn(onSetup);

      game.when(Game::isMultiplayerClient).thenReturn(false);
      game.when(Game::isHeadless).thenReturn(true);
      game.when(Game::network).thenReturn(network);

      setup.invoke(gameLoop, true);

      verify(onSetup).execute();
      verify(network).start();
    }
  }

  private ConnectionListener createConnectionListener(GameLoop gameLoop)
      throws Exception {

    for (int i = 1; i <= 20; i++) {
      Class<?> listenerClass;

      try {
        listenerClass = Class.forName("engine.game.GameLoop$" + i);
      } catch (ClassNotFoundException e) {
        continue;
      }

      if (!ConnectionListener.class.isAssignableFrom(listenerClass)) {
        continue;
      }

      Constructor<?> constructor = listenerClass.getDeclaredConstructor(GameLoop.class);
      constructor.setAccessible(true);

      return (ConnectionListener) constructor.newInstance(gameLoop);
    }

    throw new IllegalStateException("ConnectionListener nicht gefunden");
  }

  @Test
  void connectionResetsInitialWorldHandshake() throws Exception {
    Constructor<GameLoop> constructor = GameLoop.class.getDeclaredConstructor();
    constructor.setAccessible(true);
    GameLoop gameLoop = constructor.newInstance();

    Field completeReceived = GameLoop.class.getDeclaredField("initialWorldCompleteReceived");
    Field readySent = GameLoop.class.getDeclaredField("initialWorldReadySent");
    Field clientReady = GameLoop.class.getDeclaredField("initialWorldClientReady");

    completeReceived.setAccessible(true);
    readySent.setAccessible(true);
    clientReady.setAccessible(true);

    completeReceived.setBoolean(gameLoop, true);
    readySent.setBoolean(gameLoop, true);
    clientReady.setBoolean(gameLoop, true);

    ConnectionListener listener = createConnectionListener(gameLoop);

    listener.onConnected();

    assertFalse(completeReceived.getBoolean(gameLoop));
    assertFalse(readySent.getBoolean(gameLoop));
    assertFalse(clientReady.getBoolean(gameLoop));
  }

  @Test
  void disconnectBeforeGameplayDoesNotExitGame() throws Exception {
    Constructor<GameLoop> constructor = GameLoop.class.getDeclaredConstructor();
    constructor.setAccessible(true);
    GameLoop gameLoop = constructor.newInstance();

    Field clientReady = GameLoop.class.getDeclaredField("initialWorldClientReady");
    clientReady.setAccessible(true);
    clientReady.setBoolean(gameLoop, false);

    ConnectionListener listener = createConnectionListener(gameLoop);

    try (MockedStatic<Game> game = mockStatic(Game.class)) {
      game.when(Game::isMultiplayerClient).thenReturn(true);

      listener.onDisconnected("Connection lost");

      game.verify(() -> Game.exit(any(String.class)), never());
      assertFalse(clientReady.getBoolean(gameLoop));
    }
  }

  @Test
  void disconnectDuringGameplayExitsGame() throws Exception {
    Constructor<GameLoop> constructor = GameLoop.class.getDeclaredConstructor();
    constructor.setAccessible(true);
    GameLoop gameLoop = constructor.newInstance();

    Field clientReady = GameLoop.class.getDeclaredField("initialWorldClientReady");
    clientReady.setAccessible(true);
    clientReady.setBoolean(gameLoop, true);

    ConnectionListener listener = createConnectionListener(gameLoop);

    try (MockedStatic<Game> game = mockStatic(Game.class)) {
      game.when(Game::isMultiplayerClient).thenReturn(true);

      listener.onDisconnected("Connection lost");

      game.verify(() -> Game.exit(any(String.class)));

      assertFalse(clientReady.getBoolean(gameLoop));
    }
  }

  @Test
  void disconnectResetsAllHandshakeFlags() throws Exception {
    Constructor<GameLoop> constructor = GameLoop.class.getDeclaredConstructor();
    constructor.setAccessible(true);
    GameLoop gameLoop = constructor.newInstance();

    Field completeReceived = GameLoop.class.getDeclaredField("initialWorldCompleteReceived");
    Field readySent = GameLoop.class.getDeclaredField("initialWorldReadySent");
    Field clientReady = GameLoop.class.getDeclaredField("initialWorldClientReady");

    completeReceived.setAccessible(true);
    readySent.setAccessible(true);
    clientReady.setAccessible(true);

    completeReceived.setBoolean(gameLoop, true);
    readySent.setBoolean(gameLoop, true);
    clientReady.setBoolean(gameLoop, true);

    ConnectionListener listener = createConnectionListener(gameLoop);

    try (MockedStatic<Game> game = mockStatic(Game.class)) {
      game.when(Game::isMultiplayerClient).thenReturn(false);

      listener.onDisconnected("Connection lost");

      assertFalse(completeReceived.getBoolean(gameLoop));
      assertFalse(readySent.getBoolean(gameLoop));
      assertFalse(clientReady.getBoolean(gameLoop));

      game.verify(() -> Game.exit(any(String.class)), never());
    }
  }
}