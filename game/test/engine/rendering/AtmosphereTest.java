package engine.rendering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.badlogic.gdx.graphics.Color;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Atmosphere presets and copy operations. */
class AtmosphereTest {
  @Test
  void namedPresetsResolveToTheirPublishedAtmospheres() {
    assertSame(Atmosphere.NEUTRAL, Atmosphere.Preset.NEUTRAL.atmosphere());
    assertSame(Atmosphere.DAYLIGHT, Atmosphere.Preset.DAYLIGHT.atmosphere());
    assertSame(Atmosphere.DUNGEON, Atmosphere.Preset.DUNGEON.atmosphere());
    assertSame(Atmosphere.COLD, Atmosphere.Preset.COLD.atmosphere());
  }

  @Test
  void changingAmbientPreservesEveryOtherPropertyAndTheOriginal() {
    Atmosphere original = atmosphere();
    Atmosphere snapshot = snapshot(original);
    Color replacement = new Color(0.7f, 0.3f, 0.1f, 1);
    Atmosphere changed = original.withAmbient(replacement);
    assertNotSame(original, changed);
    assertEquals(
        new Atmosphere(
            replacement, snapshot.shadowTint(), snapshot.lightTint(), 0.3f, 0.6f, 0.7f, true),
        changed);
    assertEquals(snapshot, original);
  }

  @ParameterizedTest
  @ValueSource(floats = {0, 0.5f, 1, 1.25f})
  void brightnessScalesAllChannelsWithoutChangingTheOriginal(float factor) {
    Atmosphere original = atmosphere();
    Atmosphere snapshot = snapshot(original);
    Atmosphere changed = original.withBrightness(factor);
    assertNotSame(original, changed);
    assertEquals(
        new Atmosphere(
            new Color(0.2f * factor, 0.4f * factor, 0.6f * factor, 1),
            snapshot.shadowTint(),
            snapshot.lightTint(),
            0.3f,
            0.6f,
            0.7f,
            true),
        changed);
    assertEquals(snapshot, original);
  }

  @Test
  void changingLanternPreservesEveryOtherPropertyAndTheOriginal() {
    Atmosphere original = atmosphere();
    Atmosphere snapshot = snapshot(original);
    Atmosphere changed = original.withLantern(0);
    assertNotSame(original, changed);
    assertEquals(
        new Atmosphere(
            snapshot.ambient(), snapshot.shadowTint(), snapshot.lightTint(), 0.3f, 0.6f, 0, true),
        changed);
    assertEquals(snapshot, original);
  }

  private static Atmosphere atmosphere() {
    return new Atmosphere(
        new Color(0.2f, 0.4f, 0.6f, 1),
        new Color(0.8f, 0.7f, 0.9f, 1),
        new Color(0.9f, 0.8f, 0.7f, 1),
        0.3f,
        0.6f,
        0.7f,
        true);
  }

  private static Atmosphere snapshot(Atmosphere value) {
    return new Atmosphere(
        new Color(value.ambient()),
        new Color(value.shadowTint()),
        new Color(value.lightTint()),
        value.fog(),
        value.vignette(),
        value.lantern(),
        value.wallDecor());
  }
}
