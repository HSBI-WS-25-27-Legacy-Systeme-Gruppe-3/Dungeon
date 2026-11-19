package engine.rendering;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import engine.level.utils.DesignLabel;
import engine.utils.components.draw.TextureMap;
import engine.utils.components.path.IPath;
import java.lang.reflect.Field;
import java.util.BitSet;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/** Atlas analysis through the public tileset-loading boundary. */
class DesignTilesetTest {
  @Test
  void measuresOpaqueInsetsAndLeavesEnclosedTransparentHolesInsideTheWall() throws Exception {
    BitSet pixels = new BitSet();
    // Cell zero: a wall inset by two pixels on each side and four at the bottom.
    for (int y = 0; y < 12; y++) for (int x = 2; x < 14; x++) pixels.set(y * 192 + x);
    // Cell one: a fully opaque wall with an enclosed transparent pixel.
    for (int y = 0; y < 16; y++) for (int x = 16; x < 32; x++) pixels.set(y * 192 + x);
    pixels.clear(8 * 192 + 24);
    withAtlas(
        pixels,
        true,
        tileset -> {
          assertEquals(2 / 16f, tileset.leftInset(0));
          assertEquals(2 / 16f, tileset.rightInset(0));
          assertEquals(4 / 16f, tileset.bottomInset(0));
          assertArrayEquals(new int[0], tileset.floorRects(1));
          boolean[] ground = rasterize(tileset.floorRects(0));
          for (int y = 0; y < 16; y++)
            for (int x = 0; x < 16; x++)
              assertEquals(
                  x < 2 || x >= 14 || y >= 12, ground[y * 16 + x], "ground at " + x + "," + y);
          assertTrue(tileset.floorVariants().isPresent());
          assertTrue(tileset.decor());
          assertSame(tileset, DesignTileset.of(DesignLabel.RAINBOW));
        });
  }

  @Test
  void unknownPixelsAndMissingOptionalArtDoNotCreateShadowsOrDecoration() throws Exception {
    withAtlas(
        null,
        false,
        tileset -> {
          assertEquals(0, tileset.leftInset(0));
          assertEquals(0, tileset.rightInset(0));
          assertEquals(0, tileset.bottomInset(0));
          assertTrue(tileset.floorVariants().isEmpty());
          assertTrue(!tileset.decor());
          assertEquals("dungeon/rainbow/wall/walls.png", tileset.walls().pathString());
          assertArrayEquals(new int[] {0, 0, 16, 16}, tileset.floorRects(0));
        });
  }

  private static boolean[] rasterize(int[] rects) {
    boolean[] pixels = new boolean[256];
    for (int i = 0; i < rects.length; i += 4)
      for (int y = rects[i + 1]; y < rects[i + 1] + rects[i + 3]; y++)
        for (int x = rects[i]; x < rects[i] + rects[i + 2]; x++) {
          assertTrue(!pixels[y * 16 + x], "rectangles must not overlap");
          pixels[y * 16 + x] = true;
        }
    return pixels;
  }

  private static void withAtlas(BitSet pixels, boolean optionalArt, Consumer<DesignTileset> check)
      throws Exception {
    Field field = DesignTileset.class.getDeclaredField("TILESETS");
    field.setAccessible(true);
    @SuppressWarnings("unchecked")
    Map<DesignLabel, DesignTileset> cache = (Map<DesignLabel, DesignTileset>) field.get(null);
    DesignTileset previous = cache.remove(DesignLabel.RAINBOW);
    Files original = Gdx.files;
    Files files = mock(Files.class);
    FileHandle handle = mock(FileHandle.class);
    when(handle.exists()).thenReturn(optionalArt);
    when(files.internal(anyString())).thenReturn(handle);
    Texture texture = mock(Texture.class);
    when(texture.getWidth()).thenReturn(192);
    TextureMap map = mock(TextureMap.class);
    when(map.textureAt(any(IPath.class))).thenReturn(texture);
    when(map.opaquePixels(texture)).thenReturn(Optional.ofNullable(pixels));
    try (MockedStatic<TextureMap> singleton = mockStatic(TextureMap.class)) {
      singleton.when(TextureMap::instance).thenReturn(map);
      Gdx.files = files;
      check.accept(DesignTileset.of(DesignLabel.RAINBOW));
    } finally {
      Gdx.files = original;
      cache.remove(DesignLabel.RAINBOW);
      if (previous != null) cache.put(DesignLabel.RAINBOW, previous);
    }
  }
}
