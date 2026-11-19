package engine.rendering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import engine.utils.components.draw.TextureMap;
import java.util.BitSet;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/** Pixel-based contact footprints, including atlas regions and flips. */
class SpriteFootprintsTest {
  @Test
  void footprintUsesOnlyTheThreeRowsAboveTheLowestOpaquePixel() {
    BitSet pixels = new BitSet();
    pixels.set(6 * 8 + 3);
    pixels.set(5 * 8 + 2);
    pixels.set(4 * 8 + 4);
    pixels.set(3 * 8); // A wide upper body must not widen the footprint.
    withMask(
        pixels,
        region ->
            assertEquals(
                new SpriteFootprints.Footprint(0.25f, 0.625f, 0.125f),
                SpriteFootprints.of(region).orElseThrow()));
  }

  @Test
  void horizontalAndVerticalFlipsFollowTheDisplayedFeet() {
    BitSet pixels = new BitSet();
    pixels.set(7 * 8 + 1);
    pixels.set(0 * 8 + 5);
    withMask(
        pixels,
        region -> {
          region.flip(true, false);
          assertEquals(
              new SpriteFootprints.Footprint(0.75f, 0.875f, 0),
              SpriteFootprints.of(region).orElseThrow());
          region.flip(false, true);
          assertEquals(
              new SpriteFootprints.Footprint(0.25f, 0.375f, 0),
              SpriteFootprints.of(region).orElseThrow());
        });
  }

  @Test
  void atlasRegionIgnoresPixelsOutsideItsRectangle() {
    BitSet pixels = new BitSet();
    pixels.set(5 * 8 + 3);
    pixels.set(5 * 8 + 7);
    pixels.set(7 * 8);
    withMask(
        pixels,
        region -> {
          region.setRegion(2, 2, 4, 4);
          assertEquals(
              new SpriteFootprints.Footprint(0.25f, 0.5f, 0),
              SpriteFootprints.of(region).orElseThrow());
        });
  }

  @Test
  void emptyUnknownAndZeroSizedFramesHaveNoFootprint() {
    withMask(new BitSet(), region -> assertTrue(SpriteFootprints.of(region).isEmpty()));
    withMask(null, region -> assertTrue(SpriteFootprints.of(region).isEmpty()));
    withMask(
        new BitSet(),
        region -> {
          region.setRegion(0, 0, 0, 4);
          assertTrue(SpriteFootprints.of(region).isEmpty());
        });
  }

  private static void withMask(BitSet pixels, Consumer<TextureRegion> check) {
    Texture texture = mock(Texture.class);
    when(texture.getWidth()).thenReturn(8);
    when(texture.getHeight()).thenReturn(8);
    TextureMap map = mock(TextureMap.class);
    when(map.opaquePixels(texture)).thenReturn(Optional.ofNullable(pixels));
    try (MockedStatic<TextureMap> singleton = mockStatic(TextureMap.class)) {
      singleton.when(TextureMap::instance).thenReturn(map);
      check.accept(new TextureRegion(texture));
    }
  }
}
