package testingUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import engine.tracking.Tracking;
import java.lang.reflect.Field;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TrackingResetTest {

  private final TrackingReset reset = new TrackingReset();

  @Test
  void clearsSessionOutcomeAndRoom() throws Exception {
    set("clientRoomId", "room-1");
    set("sessionStartAttempted", true);
    set("trackingAllowed", false);
    set("outcome", Optional.of("ergebnis"));

    reset.before();

    assertNull(get("clientRoomId"));
    assertNull(get("session"));
    assertFalse((boolean) get("sessionStartAttempted"));
    assertTrue((boolean) get("trackingAllowed"));
    assertEquals(Optional.empty(), get("outcome"));
  }

  private static void set(String name, Object value) throws Exception {
    Field field = Tracking.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(null, value);
  }

  private static Object get(String name) throws Exception {
    Field field = Tracking.class.getDeclaredField(name);
    field.setAccessible(true);
    return field.get(null);
  }
}
