package engine.rendering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.graphics.Color;
import engine.utils.Point;
import org.junit.jupiter.api.Test;

/** Lighting configuration and flame animation contracts. */
class AtmosphereTest {
  @Test
  void adjustingAmbientBrightnessAndLanternPreservesTheOtherMoodSettings() {
    Atmosphere original = Atmosphere.DUNGEON;
    Atmosphere changed = original.withAmbient(Color.BLUE).withBrightness(0.5f).withLantern(0.2f);
    assertEquals(new Color(0, 0, 0.5f, 1), changed.ambient());
    assertEquals(original.shadowTint(), changed.shadowTint());
    assertEquals(original.lightTint(), changed.lightTint());
    assertEquals(original.fog(), changed.fog());
    assertEquals(original.vignette(), changed.vignette());
    assertEquals(original.wallDecor(), changed.wallDecor());
    assertEquals(0.2f, changed.lantern());
    assertEquals(0.85f, original.lantern());
    assertEquals(new Color(0, 0, 0, 1), original.withBrightness(0).ambient());
  }

  @Test
  void namedPresetsResolveToTheirPublishedAtmospheres() {
    assertSame(Atmosphere.NEUTRAL, Atmosphere.Preset.NEUTRAL.atmosphere());
    assertSame(Atmosphere.DAYLIGHT, Atmosphere.Preset.DAYLIGHT.atmosphere());
    assertSame(Atmosphere.DUNGEON, Atmosphere.Preset.DUNGEON.atmosphere());
    assertSame(Atmosphere.COLD, Atmosphere.Preset.COLD.atmosphere());
  }

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
