package testingUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.Input;
import engine.network.messages.c2s.InputMessage;
import engine.systems.input.InputManager;
import engine.systems.input.MovementInputSystem;
import engine.utils.Direction;
import java.lang.reflect.Field;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InputResetTest {

  private final InputReset reset = new InputReset();

  @Test
  void clearsPressedKeys() throws Exception {
    pressedKeys().add(Input.Keys.W);
    assertTrue(InputManager.isKeyPressed(Input.Keys.W));

    reset.before();

    assertFalse(InputManager.isKeyPressed(Input.Keys.W));
  }

  @Test
  void clearsHeldDirection() throws Exception {
    MovementInputSystem.hold(Direction.RIGHT);
    assertTrue(held("heldX") != 0);

    reset.before();

    assertEquals(0f, held("heldX"));
    assertEquals(0f, held("heldY"));
  }

  @Test
  void resetsInputSequence() throws Exception {
    sequence((short) 7);

    reset.before();

    assertEquals((short) 0, sequence());
  }

  @SuppressWarnings("unchecked")
  private static Set<Integer> pressedKeys() throws Exception {
    Field field = InputManager.class.getDeclaredField("pressedKeys");
    field.setAccessible(true);
    return (Set<Integer>) field.get(null);
  }

  private static float held(String name) throws Exception {
    Field field = MovementInputSystem.class.getDeclaredField(name);
    field.setAccessible(true);
    return field.getFloat(null);
  }

  private static short sequence() throws Exception {
    Field field = InputMessage.class.getDeclaredField("currentSequence");
    field.setAccessible(true);
    return field.getShort(null);
  }

  private static void sequence(short value) throws Exception {
    Field field = InputMessage.class.getDeclaredField("currentSequence");
    field.setAccessible(true);
    field.setShort(null, value);
  }
}
