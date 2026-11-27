package testingUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import engine.Game;
import engine.game.ECSManagement;
import engine.time.PlayClock;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PlayClockResetTest {

  private static final short CLIENT = 1;
  private static final int HERO = 10;

  private final PlayClockReset reset = new PlayClockReset();
  private final PlayClock clock = Game.playClock();

  @AfterEach
  void tearDown() {
    reset.before();
  }

  private static void tick(int value) throws ReflectiveOperationException {
    Field tick = ECSManagement.class.getDeclaredField("currentTick");
    tick.setAccessible(true);
    tick.setInt(null, value);
  }

  private void start() {
    clock.ready();
    clock.participantJoined(CLIENT, HERO);
  }

  @Test
  void clockIsBackAtZeroWithoutParticipants() {
    clock.restore(5_000);
    start();
    assertTrue(clock.running());

    reset.before();

    assertEquals(0, clock.activeMs());
    assertFalse(clock.running());
    assertEquals(0, clock.participantCount());
    assertFalse(clock.joined(HERO));
  }

  @Test
  void stillTheSameClock() {
    reset.before();

    assertSame(clock, Game.playClock());
  }

  @Test
  void listenersFromEarlierTestsAreGone() {
    List<PlayClock.Transition> seen = new ArrayList<>();
    clock.onTransition(seen::add);

    reset.before();
    start();

    assertTrue(clock.running());
    assertTrue(seen.isEmpty());
  }

  @Test
  void endedClockRunsAgain() {
    start();
    clock.end(PlayClock.Outcome.SUCCESS);

    reset.before();
    start();

    assertTrue(clock.running());
  }

  @Test
  void pausedClockRunsAgain() {
    start();
    clock.paused(CLIENT, true);
    assertFalse(clock.running());

    reset.before();
    start();

    assertTrue(clock.running());
  }

  @Test
  void minimumPlayersAndPlayingEntitiesAreReset() {
    clock.configure(3);
    clock.playingParticipants(Set.of(99));

    reset.before();
    start();

    assertTrue(clock.running());
  }

  @Test
  void tickCounterIsBackAtZero() throws ReflectiveOperationException {
    tick(42);
    assertEquals(42, Game.currentTick());

    reset.before();

    assertEquals(0, Game.currentTick());
  }
}
