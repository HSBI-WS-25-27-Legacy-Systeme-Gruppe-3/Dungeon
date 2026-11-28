package testingUtils;

import engine.tracking.Tracking;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.Set;

/**
 * Setzt vor jedem Test das Tracking auf den Ausgangszustand: keine laufende Session, kein Ergebnis
 * und keine Raum-Kennung. So startet kein Test eine echte Tracking-Session.
 */
public final class TrackingReset {

  /** Setzt die statischen Felder von {@link Tracking} auf ihren Anfangszustand zurück. */
  public void before() {
    try {
      set("explicitConfig", null);
      set("session", null);
      set("sessionStartAttempted", false);
      set("trackingAllowed", true);
      set("outcome", Optional.empty());
      set("clientRoomId", null);
      set("persistenceFailure", null);
      set("warningShownFor", null);
      pendingPuzzleStarts().clear();
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Tracking konnte nicht zurückgesetzt werden", e);
    }
  }

  /** Nach dem Test ist nichts zu tun, der nächste Test setzt das Tracking wieder zurück. */
  public void after() {}

  private static void set(String name, Object value) throws ReflectiveOperationException {
    Field field = Tracking.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(null, value);
  }

  private static Set<?> pendingPuzzleStarts() throws ReflectiveOperationException {
    Field field = Tracking.class.getDeclaredField("PENDING_PUZZLE_STARTS");
    field.setAccessible(true);
    return (Set<?>) field.get(null);
  }
}
