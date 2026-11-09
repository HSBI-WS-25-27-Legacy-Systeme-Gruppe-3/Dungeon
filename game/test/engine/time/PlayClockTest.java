package engine.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import engine.time.PlayClock.Event;
import engine.time.PlayClock.Outcome;
import engine.time.PlayClock.PauseReason;
import engine.time.PlayClock.Transition;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// Tests für PlayClock: Start, Pause, Ende und die gezählte Spielzeit
class PlayClockTest {

  private static final short CLIENT_A = 1;
  private static final short CLIENT_B = 2;
  private static final short UNKNOWN_CLIENT = 99;
  private static final int ENTITY_A = 10;
  private static final int ENTITY_B = 20;

  private static final Transition STARTED =
      new Transition(Event.STARTED, Optional.empty(), Optional.empty());
  private static final Transition RESUMED =
      new Transition(Event.RESUMED, Optional.empty(), Optional.empty());

  private final List<Transition> transitions = new ArrayList<>();
  private long nowMs;
  private PlayClock clock;

  @BeforeEach
  void setUp() {
    nowMs = 1_000;
    clock = new PlayClock(() -> nowMs);
    clock.onTransition(transitions::add);
  }

  private static Transition paused(PauseReason reason) {
    return new Transition(Event.PAUSED, Optional.of(reason), Optional.empty());
  }

  private static Transition ended(Outcome outcome) {
    return new Transition(Event.ENDED, Optional.empty(), Optional.of(outcome));
  }

  private void advance(long ms) {
    nowMs += ms;
  }

  private void startWithOnePlayer() {
    clock.ready();
    clock.participantJoined(CLIENT_A, ENTITY_A);
    transitions.clear();
  }

  private void startWithTwoPlayers(int minimumPlayers) {
    clock.configure(minimumPlayers);
    clock.ready();
    clock.participantJoined(CLIENT_A, ENTITY_A);
    clock.participantJoined(CLIENT_B, ENTITY_B);
    transitions.clear();
  }

  @Nested
  class Start {

    @Test
    void worksWithSystemTimeAndWithoutListener() {
      PlayClock systemClock = new PlayClock();

      systemClock.ready();
      systemClock.participantJoined(CLIENT_A, ENTITY_A);

      assertTrue(systemClock.running());
      assertTrue(systemClock.activeMs() >= 0);
    }

    @Test
    void staysStoppedWithoutReady() {
      clock.participantJoined(CLIENT_A, ENTITY_A);

      assertFalse(clock.running());
      assertTrue(transitions.isEmpty());
    }

    @Test
    void staysStoppedWithoutParticipants() {
      clock.ready();

      assertFalse(clock.running());
      assertTrue(transitions.isEmpty());
    }

    @Test
    void startsWhenReadyFollowsJoin() {
      clock.participantJoined(CLIENT_A, ENTITY_A);

      clock.ready();

      assertTrue(clock.running());
      assertEquals(List.of(STARTED), transitions);
    }

    @Test
    void startsWhenJoinFollowsReady() {
      clock.ready();

      clock.participantJoined(CLIENT_A, ENTITY_A);

      assertTrue(clock.running());
      assertEquals(List.of(STARTED), transitions);
    }

    @Test
    void waitsForMinimumPlayers() {
      clock.configure(2);
      clock.ready();

      clock.participantJoined(CLIENT_A, ENTITY_A);
      assertFalse(clock.running());

      clock.participantJoined(CLIENT_B, ENTITY_B);
      assertTrue(clock.running());
      assertEquals(List.of(STARTED), transitions);
    }

    @Test
    void sameClientJoiningTwiceCountsOnce() {
      clock.configure(2);
      clock.ready();

      clock.participantJoined(CLIENT_A, ENTITY_A);
      clock.participantJoined(CLIENT_A, ENTITY_A);

      assertEquals(1, clock.participantCount());
      assertFalse(clock.running());
    }

    @Test
    void onlyParticipantsPastTheIntroGateCount() {
      clock.configure(2);
      clock.participantJoined(CLIENT_A, ENTITY_A);
      clock.participantJoined(CLIENT_B, ENTITY_B);
      clock.playingParticipants(Set.of(ENTITY_A));
      clock.ready();
      assertFalse(clock.running());

      clock.playingParticipants(Set.of(ENTITY_A, ENTITY_B));

      assertTrue(clock.running());
    }

    @Test
    void waitsWhileEveryPlayerHasThePauseMenuOpen() {
      clock.participantJoined(CLIENT_A, ENTITY_A);
      clock.paused(CLIENT_A, true);
      clock.ready();
      assertFalse(clock.running());

      clock.paused(CLIENT_A, false);

      assertEquals(List.of(STARTED), transitions);
    }

    @Test
    void rejectsMinimumPlayersBelowOne() {
      assertThrows(IllegalArgumentException.class, () -> clock.configure(0));
    }
  }

  @Nested
  class ActiveTime {

    @Test
    void isZeroBeforeStart() {
      advance(5_000);

      assertEquals(0, clock.activeMs());
    }

    @Test
    void countsOnlyFromStartOn() {
      advance(5_000);
      startWithOnePlayer();

      advance(1_500);

      assertEquals(1_500, clock.activeMs());
    }

    @Test
    void leavesOutPausesAndSumsTheRunningPhases() {
      startWithOnePlayer();
      advance(1_000);

      clock.paused(CLIENT_A, true);
      advance(60_000);
      assertEquals(1_000, clock.activeMs());

      clock.paused(CLIENT_A, false);
      advance(500);
      assertEquals(1_500, clock.activeMs());
    }

    @Test
    void neverGoesBackWhenTheTimeSourceJumpsBack() {
      startWithOnePlayer();

      advance(-500);

      assertEquals(0, clock.activeMs());
    }
  }

  @Nested
  class Pause {

    @Test
    void stopsWithPauseDialogReason() {
      startWithOnePlayer();

      clock.paused(CLIENT_A, true);

      assertFalse(clock.running());
      assertEquals(List.of(paused(PauseReason.PAUSE_DIALOG)), transitions);
    }

    @Test
    void resumesWithoutReason() {
      startWithOnePlayer();
      clock.paused(CLIENT_A, true);
      transitions.clear();

      clock.paused(CLIENT_A, false);

      assertTrue(clock.running());
      assertEquals(List.of(RESUMED), transitions);
    }

    @Test
    void repeatedPauseReportsOnlyOnce() {
      startWithOnePlayer();

      clock.paused(CLIENT_A, true);
      clock.paused(CLIENT_A, true);

      assertEquals(List.of(paused(PauseReason.PAUSE_DIALOG)), transitions);
    }

    @Test
    void keepsRunningWhileOneOfTwoPlayersPlays() {
      startWithTwoPlayers(1);

      clock.paused(CLIENT_A, true);

      assertTrue(clock.running());
      assertTrue(transitions.isEmpty());
    }

    @Test
    void stopsOnlyWhenAllPlayersPause() {
      startWithTwoPlayers(1);
      clock.paused(CLIENT_A, true);

      clock.paused(CLIENT_B, true);

      assertFalse(clock.running());
      assertEquals(List.of(paused(PauseReason.PAUSE_DIALOG)), transitions);
    }

    @Test
    void resumesWhenOnePlayerReturns() {
      startWithTwoPlayers(1);
      clock.paused(CLIENT_A, true);
      clock.paused(CLIENT_B, true);
      transitions.clear();

      clock.paused(CLIENT_B, false);

      assertTrue(clock.running());
      assertEquals(List.of(RESUMED), transitions);
    }

    @Test
    void ignoresUnknownClients() {
      startWithOnePlayer();

      clock.paused(UNKNOWN_CLIENT, true);

      assertTrue(clock.running());
      assertTrue(transitions.isEmpty());
    }

    @Test
    void doesNotRememberPausesOfClientsThatJoinLater() {
      startWithOnePlayer();
      clock.paused(CLIENT_B, true);
      clock.participantJoined(CLIENT_B, ENTITY_B);

      clock.paused(CLIENT_A, true);

      assertTrue(clock.running());
      assertTrue(transitions.isEmpty());
    }

    @Test
    void participantsOutsideTheIntroGateDoNotKeepTheClockRunning() {
      startWithTwoPlayers(1);
      clock.playingParticipants(Set.of(ENTITY_A));

      clock.paused(CLIENT_A, true);

      assertFalse(clock.running());
      assertEquals(List.of(paused(PauseReason.PAUSE_DIALOG)), transitions);
    }
  }

  @Nested
  class Participants {

    @Test
    void stopsWhenTooFewPlayersRemain() {
      startWithTwoPlayers(2);

      clock.participantLeft(CLIENT_B);

      assertFalse(clock.running());
      assertEquals(List.of(paused(PauseReason.PLAYERS_MISSING)), transitions);
    }

    @Test
    void resumesWhenAPlayerReturns() {
      startWithTwoPlayers(2);
      clock.participantLeft(CLIENT_B);
      transitions.clear();

      clock.participantJoined(CLIENT_B, ENTITY_B);

      assertTrue(clock.running());
      assertEquals(List.of(RESUMED), transitions);
    }

    @Test
    void keepsRunningWhileEnoughPlayersRemain() {
      startWithTwoPlayers(1);

      clock.participantLeft(CLIENT_B);

      assertTrue(clock.running());
      assertTrue(transitions.isEmpty());
    }

    @Test
    void reportsChangedReasonWhileAlreadyStopped() {
      startWithTwoPlayers(2);
      clock.paused(CLIENT_A, true);
      clock.paused(CLIENT_B, true);
      advance(1_000);
      long activeBefore = clock.activeMs();

      clock.participantLeft(CLIENT_B);

      assertEquals(
          List.of(paused(PauseReason.PAUSE_DIALOG), paused(PauseReason.PLAYERS_MISSING)),
          transitions);
      assertEquals(activeBefore, clock.activeMs());
    }

    @Test
    void leavingClearsThePauseOfThatClient() {
      startWithOnePlayer();
      clock.paused(CLIENT_A, true);
      clock.participantLeft(CLIENT_A);
      transitions.clear();

      clock.participantJoined(CLIENT_A, ENTITY_A);

      assertTrue(clock.running());
      assertEquals(List.of(RESUMED), transitions);
    }

    @Test
    void reportsConnectedParticipants() {
      clock.participantJoined(CLIENT_A, ENTITY_A);
      clock.participantJoined(CLIENT_B, ENTITY_B);

      clock.participantLeft(CLIENT_B);

      assertEquals(1, clock.participantCount());
      assertTrue(clock.joined(ENTITY_A));
      assertFalse(clock.joined(ENTITY_B));
    }
  }

  @Nested
  class End {

    @Test
    void stopsAndKeepsTheTimeAndOutcome() {
      startWithOnePlayer();
      advance(2_000);

      clock.end(Outcome.SUCCESS);
      advance(5_000);

      assertFalse(clock.running());
      assertEquals(2_000, clock.activeMs());
      assertEquals(List.of(ended(Outcome.SUCCESS)), transitions);
    }

    @Test
    void endingTwiceKeepsTheFirstResult() {
      startWithOnePlayer();

      clock.end(Outcome.FAILURE);
      clock.end(Outcome.SUCCESS);

      assertEquals(List.of(ended(Outcome.FAILURE)), transitions);
    }

    @Test
    void cannotBeRestartedByLaterEvents() {
      startWithOnePlayer();
      clock.paused(CLIENT_A, true);
      clock.end(Outcome.SUCCESS);
      transitions.clear();

      clock.paused(CLIENT_A, false);
      clock.participantJoined(CLIENT_B, ENTITY_B);
      clock.ready();

      assertFalse(clock.running());
      assertTrue(transitions.isEmpty());
    }

    @Test
    void whilePausedKeepsThePausedTime() {
      startWithOnePlayer();
      advance(1_000);
      clock.paused(CLIENT_A, true);
      advance(9_000);
      transitions.clear();

      clock.end(Outcome.SUCCESS);

      assertEquals(1_000, clock.activeMs());
      assertEquals(List.of(ended(Outcome.SUCCESS)), transitions);
    }

    @Test
    void beforeStartPreventsTheStart() {
      clock.end(Outcome.FAILURE);

      clock.ready();
      clock.participantJoined(CLIENT_A, ENTITY_A);

      assertFalse(clock.running());
      assertEquals(0, clock.activeMs());
      assertEquals(List.of(ended(Outcome.FAILURE)), transitions);
    }

    @Test
    void rejectsMissingOutcome() {
      assertThrows(NullPointerException.class, () -> clock.end(null));
    }
  }

  @Nested
  class Restore {

    @Test
    void continuesFromTheSavedTime() {
      clock.restore(30_000);
      advance(5_000);
      startWithOnePlayer();

      advance(1_000);

      assertEquals(31_000, clock.activeMs());
    }

    @Test
    void isAllowedUntilPlayStarts() {
      clock.participantJoined(CLIENT_A, ENTITY_A);

      clock.restore(1_000);

      assertEquals(1_000, clock.activeMs());
    }

    @Test
    void rejectsNegativeTime() {
      assertThrows(IllegalArgumentException.class, () -> clock.restore(-1));
    }

    @Test
    void rejectsRestoreAfterPlayStarted() {
      startWithOnePlayer();

      assertThrows(IllegalStateException.class, () -> clock.restore(1_000));
    }
  }

  @Nested
  class Synchronize {

    @Test
    void advancesLocallyWhileTheServerClockRuns() {
      clock.synchronize(10_000, true);

      advance(250);

      assertTrue(clock.running());
      assertEquals(10_250, clock.activeMs());
    }

    @Test
    void holdsTheServerTimeWhileTheServerClockStands() {
      clock.synchronize(10_000, false);

      advance(250);

      assertFalse(clock.running());
      assertEquals(10_000, clock.activeMs());
    }

    @Test
    void laterSampleReplacesTheLocalEstimate() {
      clock.synchronize(10_000, true);
      advance(5_000);

      clock.synchronize(12_000, true);

      assertEquals(12_000, clock.activeMs());
    }

    @Test
    void doesNotReportTransitions() {
      clock.synchronize(10_000, true);

      assertTrue(transitions.isEmpty());
    }

    @Test
    void rejectsNegativeTime() {
      assertThrows(IllegalArgumentException.class, () -> clock.synchronize(-1, true));
    }
  }

  @Nested
  class Configure {

    @Test
    void resetsTheClockForANewRoom() {
      startWithOnePlayer();
      advance(1_000);
      clock.end(Outcome.SUCCESS);

      clock.configure(1);

      assertFalse(clock.running());
      assertEquals(0, clock.activeMs());
      assertEquals(0, clock.participantCount());
    }

    @Test
    void requiresReadyAgainButKeepsTheListener() {
      startWithOnePlayer();
      clock.configure(1);

      clock.participantJoined(CLIENT_A, ENTITY_A);
      assertFalse(clock.running());

      clock.ready();
      assertEquals(List.of(STARTED), transitions);
    }

    @Test
    void forgetsThePauseReasonOfTheOldRoom() {
      startWithOnePlayer();
      clock.paused(CLIENT_A, true);
      clock.configure(1);
      transitions.clear();

      clock.ready();
      clock.participantJoined(CLIENT_A, ENTITY_A);

      assertEquals(List.of(STARTED), transitions);
    }

    @Test
    void forgetsThePausesAndTheIntroGateOfTheOldRoom() {
      clock.participantJoined(CLIENT_A, ENTITY_A);
      clock.paused(CLIENT_A, true);
      clock.playingParticipants(Set.of(ENTITY_B));

      clock.configure(1);
      clock.participantJoined(CLIENT_A, ENTITY_A);
      clock.ready();

      assertTrue(clock.running());
    }
  }

  @Nested
  class Listener {

    @Test
    void rejectsNull() {
      assertThrows(NullPointerException.class, () -> clock.onTransition(null));
    }

    @Test
    void replacesThePreviousListener() {
      List<Transition> replacement = new ArrayList<>();
      clock.onTransition(replacement::add);

      startWithOnePlayer();

      assertTrue(transitions.isEmpty());
      assertEquals(List.of(STARTED), replacement);
    }

    // Würde der Listener die Uhr sperren, käme der andere Thread nicht an die Zeit
    @Test
    void runsOutsideTheLockOfTheClock() {
      List<Long> readByOtherThread = new ArrayList<>();
      clock.onTransition(
          transition -> {
            try {
              readByOtherThread.add(
                  CompletableFuture.supplyAsync(clock::activeMs).get(5, TimeUnit.SECONDS));
            } catch (Exception e) {
              throw new AssertionError("clock stayed locked during the listener call", e);
            }
          });

      startWithOnePlayer();

      assertEquals(List.of(0L), readByOtherThread);
    }
  }
}
