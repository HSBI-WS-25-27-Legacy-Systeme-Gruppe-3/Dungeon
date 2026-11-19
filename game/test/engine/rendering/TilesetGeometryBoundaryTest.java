package engine.rendering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;

/** Synthetic atlas geometry exercised through the existing loading boundary. */
class TilesetGeometryBoundaryTest {
  @ParameterizedTest
  @CsvSource({"0", "13", "35", "47"})
  void asymmetricInsetsAreMeasuredInEveryAtlasRow(int cell) throws Exception {
    BitSet opaque = new BitSet();
    for (int y = 1; y < 11; y++) for (int x = 3; x < 14; x++) opaque.set(index(cell, x, y));
    withAtlas(
        opaque,
        tileset -> {
          assertEquals(3 / 16f, tileset.leftInset(cell));
          assertEquals(2 / 16f, tileset.rightInset(cell));
          assertEquals(5 / 16f, tileset.bottomInset(cell));
        });
  }

  @ParameterizedTest
  @CsvSource({"0,8,15", "24,8,0", "37,15,8", "39,0,8"})
  void transparentPocketsTouchingOnlyAClosedSideStayInsideTheWall(int cell, int x, int y)
      throws Exception {
    BitSet opaque = opaqueAtlas();
    opaque.clear(index(cell, x, y));
    withAtlas(opaque, tileset -> assertGround(tileset.floorRects(cell), new BitSet()));
  }

  @ParameterizedTest
  @CsvSource({"25,15,0", "1,15,15", "3,0,15", "27,0,0"})
  void missingDiagonalOpensItsCornerEvenWhenBothSidesConnect(int cell, int x, int y)
      throws Exception {
    BitSet opaque = opaqueAtlas();
    opaque.clear(index(cell, x, y));
    BitSet expected = new BitSet();
    expected.set(y * 16 + x);
    withAtlas(opaque, tileset -> assertGround(tileset.floorRects(cell), expected));
  }

  @Test
  void fullyConnectedCellDoesNotExposeTransparentWallInterior() throws Exception {
    withAtlas(new BitSet(), tileset -> assertGround(tileset.floorRects(33), new BitSet()));
  }

  @Test
  void irregularGroundIsCoveredExactlyOnceAndEnclosedHolesStayEmpty() throws Exception {
    BitSet opaque = opaqueAtlas();
    BitSet expected = new BitSet();
    // Cell 36 has no connected sides: a branching path enters from its left edge.
    for (int x = 0; x < 12; x++) expected.set(5 * 16 + x);
    for (int y = 2; y < 13; y++) {
      expected.set(y * 16 + 6);
      expected.set(y * 16 + 7);
    }
    for (int x = 6; x < 14; x++) expected.set(12 * 16 + x);
    for (int p = expected.nextSetBit(0); p >= 0; p = expected.nextSetBit(p + 1))
      opaque.clear(index(36, p % 16, p / 16));
    opaque.clear(index(36, 13, 2)); // Isolated transparent hole, not ground.
    withAtlas(opaque, tileset -> assertGround(tileset.floorRects(36), expected));
  }

  @Test
  void emptyCellHasNoInsetsAndItsOpenGroundCoversTheWholeCell() throws Exception {
    BitSet expected = new BitSet();
    expected.set(0, 256);
    withAtlas(
        new BitSet(),
        tileset -> {
          assertEquals(0, tileset.leftInset(36));
          assertEquals(0, tileset.rightInset(36));
          assertEquals(0, tileset.bottomInset(36));
          assertGround(tileset.floorRects(36), expected);
        });
  }

  private static void assertGround(int[] rectangles, BitSet expected) {
    assertEquals(0, rectangles.length % 4);
    BitSet actual = new BitSet();
    for (int i = 0; i < rectangles.length; i += 4) {
      int x = rectangles[i];
      int y = rectangles[i + 1];
      int width = rectangles[i + 2];
      int height = rectangles[i + 3];
      assertTrue(x >= 0 && y >= 0 && width > 0 && height > 0);
      assertTrue(x + width <= 16 && y + height <= 16);
      for (int row = y; row < y + height; row++)
        for (int column = x; column < x + width; column++) {
          int pixel = row * 16 + column;
          assertFalse(actual.get(pixel), "overlapping ground at " + column + "," + row);
          actual.set(pixel);
        }
    }
    assertEquals(expected, actual);
  }

  private static int index(int cell, int x, int y) {
    return ((cell / 12) * 16 + y) * 192 + (cell % 12) * 16 + x;
  }

  private static BitSet opaqueAtlas() {
    BitSet opaque = new BitSet();
    opaque.set(0, 192 * 64);
    return opaque;
  }

  private static void withAtlas(BitSet opaque, Consumer<DesignTileset> check) throws Exception {
    // The production API caches by design. Preserve the cache to keep test order irrelevant.
    Field field = DesignTileset.class.getDeclaredField("TILESETS");
    field.setAccessible(true);
    @SuppressWarnings("unchecked")
    Map<DesignLabel, DesignTileset> cache = (Map<DesignLabel, DesignTileset>) field.get(null);
    DesignTileset previous = cache.remove(DesignLabel.RAINBOW);
    Files original = Gdx.files;
    Files files = mock(Files.class);
    when(files.internal(anyString())).thenReturn(mock(FileHandle.class));
    Texture texture = mock(Texture.class);
    when(texture.getWidth()).thenReturn(192);
    TextureMap map = mock(TextureMap.class);
    when(map.textureAt(any(IPath.class))).thenReturn(texture);
    when(map.opaquePixels(texture)).thenReturn(Optional.of(opaque));
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
