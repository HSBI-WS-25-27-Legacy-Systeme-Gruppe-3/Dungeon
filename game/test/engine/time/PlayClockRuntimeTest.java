package engine.time;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import engine.Entity;
import engine.Game;
import engine.network.handler.INetworkHandler;
import engine.network.messages.NetworkMessage;
import engine.network.messages.c2s.PauseStateMessage;
import engine.network.messages.s2c.PlayClockMessage;
import engine.time.PlayClock.Outcome;
import feature.systems.EventScheduler;
import feature.systems.HudSystem;
import java.lang.reflect.Field;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

// Tests für PlayClockRuntime: was Singleplayer, Client und Server mit der Uhr machen
class PlayClockRuntimeTest {

  private static final short SINGLEPLAYER_CLIENT = 0;
  private static final short SERVER = 0;
  private static final int PLAYER_ENTITY = 10;

  private final INetworkHandler network = mock(INetworkHandler.class);
  private final HudSystem hud = mock(HudSystem.class);
  private final Entity player = mock(Entity.class);

  private MockedStatic<Game> game;
  private MockedStatic<HudSystem> hudSystem;
  private MockedStatic<EventScheduler> scheduler;
  private long nowMs;
  private PlayClock clock;

  @BeforeEach
  void setUp() throws ReflectiveOperationException {
    // PlayClockRuntime merkt sich ihren Zustand statisch, deshalb vor jedem Test zurücksetzen
    resetStaticField("reportedPause");
    resetStaticField("lastBroadcastRunning");

    clock = new PlayClock(() -> nowMs);
    game = mockStatic(Game.class);
    hudSystem = mockStatic(HudSystem.class);
    scheduler = mockStatic(EventScheduler.class);
    game.when(Game::playClock).thenReturn(clock);
    game.when(Game::network).thenReturn(network);
    game.when(Game::player).thenReturn(Optional.of(player));
    hudSystem.when(HudSystem::getInstance).thenReturn(hud);
  }

  @AfterEach
  void tearDown() {
    scheduler.close();
    hudSystem.close();
    game.close();
  }

  private static void resetStaticField(String name) throws ReflectiveOperationException {
    Field field = PlayClockRuntime.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(null, null);
  }

  private void singleplayer() {
    game.when(Game::isSingleplayer).thenReturn(true);
    clock.ready();
    clock.participantJoined(SINGLEPLAYER_CLIENT, PLAYER_ENTITY);
  }

  private void multiplayerClient(boolean connected) {
    game.when(Game::isMultiplayerClient).thenReturn(true);
    when(network.isConnected()).thenReturn(connected);
  }

  private void server() {
    clock.ready();
    clock.participantJoined(SERVER, PLAYER_ENTITY);
  }

  private void pauseMenu(boolean open) {
    when(hud.hasOpenPlayPauseUI(player)).thenReturn(open);
  }

  private void verifyNoPauseMessageSent() {
    verify(network, never()).send(anyShort(), any(NetworkMessage.class), anyBoolean());
  }

  private void verifyNoBroadcast() {
    verify(network, never()).broadcast(any(NetworkMessage.class), anyBoolean());
  }

  private void verifyPlayActionsExecuted(int times) {
    scheduler.verify(EventScheduler::executePlayActions, times(times));
  }

  @Nested
  class LocalPauseInSingleplayer {

    @Test
    void pausesTheClockDirectly() {
      singleplayer();
      pauseMenu(true);

      PlayClockRuntime.updateLocalPause();

      assertFalse(clock.running());
      verifyNoPauseMessageSent();
    }

    @Test
    void resumesTheClockWhenTheMenuCloses() {
      singleplayer();
      pauseMenu(true);
      PlayClockRuntime.updateLocalPause();
      pauseMenu(false);

      PlayClockRuntime.updateLocalPause();

      assertTrue(clock.running());
    }

    @Test
    void doesNothingWithoutAPlayer() {
      singleplayer();
      game.when(Game::player).thenReturn(Optional.empty());

      PlayClockRuntime.updateLocalPause();

      assertTrue(clock.running());
    }
  }

  @Nested
  class LocalPauseOnMultiplayerClient {

    @Test
    void sendsThePauseStateReliablyToTheServer() {
      multiplayerClient(true);
      pauseMenu(true);

      PlayClockRuntime.updateLocalPause();

      verify(network).send(SERVER, new PauseStateMessage(true), true);
    }

    @Test
    void sendsAnUnchangedStateOnlyOnce() {
      multiplayerClient(true);
      pauseMenu(true);

      PlayClockRuntime.updateLocalPause();
      PlayClockRuntime.updateLocalPause();

      verify(network, times(1)).send(SERVER, new PauseStateMessage(true), true);
    }

    @Test
    void sendsAgainWhenTheStateChanges() {
      multiplayerClient(true);
      pauseMenu(true);
      PlayClockRuntime.updateLocalPause();
      pauseMenu(false);

      PlayClockRuntime.updateLocalPause();

      verify(network).send(SERVER, new PauseStateMessage(false), true);
    }

    @Test
    void sendsNothingWhileDisconnected() {
      multiplayerClient(false);
      pauseMenu(true);

      PlayClockRuntime.updateLocalPause();

      verifyNoPauseMessageSent();
    }

    @Test
    void sendsThePendingStateOnceConnected() {
      multiplayerClient(false);
      pauseMenu(true);
      PlayClockRuntime.updateLocalPause();
      when(network.isConnected()).thenReturn(true);

      PlayClockRuntime.updateLocalPause();

      verify(network).send(SERVER, new PauseStateMessage(true), true);
    }

    @Test
    void sendsTheSameStateAgainAfterResetClient() {
      multiplayerClient(true);
      pauseMenu(true);
      PlayClockRuntime.updateLocalPause();

      PlayClockRuntime.resetClient();
      PlayClockRuntime.updateLocalPause();

      verify(network, times(2)).send(SERVER, new PauseStateMessage(true), true);
    }

    @Test
    void leavesTheLocalClockToTheServer() {
      multiplayerClient(true);
      clock.synchronize(1_000, true);
      pauseMenu(true);

      PlayClockRuntime.updateLocalPause();

      assertTrue(clock.running());
    }
  }

  @Nested
  class TickOnMultiplayerClient {

    @Test
    void neitherRunsPlayActionsNorBroadcasts() {
      multiplayerClient(true);

      PlayClockRuntime.tick();

      verifyPlayActionsExecuted(0);
      verifyNoBroadcast();
    }
  }

  @Nested
  class TickInSingleplayer {

    @Test
    void runsPlayActionsWithoutBroadcast() {
      singleplayer();

      PlayClockRuntime.tick();

      verifyPlayActionsExecuted(1);
      verifyNoBroadcast();
    }
  }

  @Nested
  class TickOnServer {

    @Test
    void runsPlayActionsEveryTick() {
      server();

      PlayClockRuntime.tick();
      PlayClockRuntime.tick();

      verifyPlayActionsExecuted(2);
    }

    @Test
    void broadcastsTheFirstStateReliably() {
      server();
      nowMs += 1_500;

      PlayClockRuntime.tick();

      verify(network).broadcast(new PlayClockMessage(1_500, true), true);
    }

    @Test
    void doesNotRepeatAnUnchangedState() {
      server();

      PlayClockRuntime.tick();
      nowMs += 1_000;
      PlayClockRuntime.tick();

      verify(network, times(1)).broadcast(any(NetworkMessage.class), anyBoolean());
    }

    @Test
    void broadcastsWhenTheClockStops() {
      server();
      PlayClockRuntime.tick();
      nowMs += 2_000;
      clock.paused(SERVER, true);

      PlayClockRuntime.tick();

      verify(network).broadcast(new PlayClockMessage(2_000, false), true);
    }

    @Test
    void broadcastsWhenTheClockRunsAgain() {
      server();
      PlayClockRuntime.tick();
      nowMs += 1_000;
      clock.paused(SERVER, true);
      PlayClockRuntime.tick();
      nowMs += 9_000;
      clock.paused(SERVER, false);

      PlayClockRuntime.tick();

      verify(network).broadcast(new PlayClockMessage(1_000, true), true);
    }

    @Test
    void broadcastsTheResultOfAPlayActionFromTheSameTick() {
      server();
      PlayClockRuntime.tick();
      scheduler.when(EventScheduler::executePlayActions).then(call -> endClock());

      PlayClockRuntime.tick();

      verify(network).broadcast(new PlayClockMessage(0, false), true);
    }

    private Void endClock() {
      clock.end(Outcome.FAILURE);
      return null;
    }
  }
}
