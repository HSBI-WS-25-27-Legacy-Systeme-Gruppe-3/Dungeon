package engine.rendering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.graphics.Color;
import engine.utils.Point;
import org.junit.jupiter.api.Test;

/** Light reach and flame animation contracts. */
class LightTest {
  @Test
  void steadyLightsScaleReachAndHaveNoFireEffects() {
    assertEquals(
        new Light(new Point(2, 3), Color.RED, 4, 0.8f, 0, false),
        Light.steady(new Point(2, 3), Color.RED, 0.8f));
    assertEquals(0, Light.steady(new Point(0, 0), Color.WHITE, 0).radius());
  }

  @Test
  void flameWobbleIsBoundedRepeatableAndVariesWithTimeAndPosition() {
    Point p = new Point(2, 3);
    for (int i = 0; i < 1000; i++) {
      float wobble = Light.wobble(p, i * 0.03f);
      assertTrue(wobble >= -1 && wobble <= 1);
      assertEquals(wobble, Light.wobble(p, i * 0.03f));
    }
    assertNotEquals(Light.wobble(p, 0), Light.wobble(p, 1));
    assertNotEquals(Light.wobble(p, 0), Light.wobble(new Point(3, 3), 0));
  }
}
