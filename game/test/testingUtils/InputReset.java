package testingUtils;

import engine.network.messages.c2s.InputMessage;
import engine.systems.input.InputManager;
import engine.systems.input.MovementInputSystem;
import java.lang.reflect.Field;

/**
 * Setzt vor jedem Test die Eingabe auf den Ausgangszustand: keine Taste und kein Mausbutton
 * gedrückt, keine gehaltene Bewegungsrichtung und die Sequenznummer der Eingabe auf 0.
 */
public final class InputReset {

  /** Setzt Tasten-, Maus- und Richtungszustand sowie die Sequenznummer zurück. */
  public void before() {
    InputManager.reset();
    clearHeldDirection();
    InputMessage.resetSequence();
  }

  /** Nach dem Test ist nichts zu tun, der nächste Test setzt die Eingabe wieder zurück. */
  public void after() {}

  private static void clearHeldDirection() {
    try {
      for (String name : new String[] {"heldX", "heldY"}) {
        Field field = MovementInputSystem.class.getDeclaredField(name);
        field.setAccessible(true);
        field.setFloat(null, 0f);
      }
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Bewegungsrichtung konnte nicht zurückgesetzt werden", e);
    }
  }
}
