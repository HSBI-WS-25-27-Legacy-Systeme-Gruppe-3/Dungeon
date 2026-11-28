package testingUtils;

import engine.Game;
import engine.game.ECSManagement;
import engine.time.PlayClock;
import engine.tracking.TrackingRuntime;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Map;

/** Setzt vor jedem Test die Spieluhr und den Tick-Zähler auf ihren Anfangszustand. */
public final class PlayClockReset {

  /** Setzt {@link Game#playClock()} und {@link Game#currentTick()} zurück. */
  public void before() {
    reset(Game.playClock());
    try {
      Field tick = ECSManagement.class.getDeclaredField("currentTick");
      tick.setAccessible(true);
      tick.setInt(null, 0);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Tick-Zähler konnte nicht zurückgesetzt werden", e);
    }
  }

  /** Nach dem Test ist nichts zu tun, der nächste Test setzt die Uhr wieder zurück. */
  public void after() {}

  /**
   * Setzt eine Uhr auf den Stand einer neuen Uhr zurück. Die Uhr in {@link Game} ist {@code static
   * final} und kann deshalb nicht ersetzt werden.
   *
   * @param clock die Uhr, die zurückgesetzt wird
   */
  public static void reset(PlayClock clock) {
    PlayClock fresh = new PlayClock();
    try {
      synchronized (clock) {
        for (Field field : PlayClock.class.getDeclaredFields()) {
          if (Modifier.isStatic(field.getModifiers())) continue;
          field.setAccessible(true);
          Object value = field.get(fresh);
          if (!Modifier.isFinal(field.getModifiers())) {
            field.set(clock, value);
          } else if (value instanceof Map<?, ?>) {
            ((Map<?, ?>) field.get(clock)).clear();
          } else if (value instanceof Collection<?>) {
            ((Collection<?>) field.get(clock)).clear();
          }
        }
      }
    } catch (IllegalAccessException e) {
      throw new IllegalStateException("Spieluhr konnte nicht zurückgesetzt werden", e);
    }
    clock.onTransition(TrackingRuntime::playClockChanged);
  }
}
