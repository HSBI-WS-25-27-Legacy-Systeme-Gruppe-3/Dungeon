package engine.rendering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import engine.level.Tile;
import engine.level.elements.tile.DoorTile;
import engine.level.elements.tile.FloorTile;
import engine.level.elements.tile.WallTile;
import engine.level.utils.Coordinate;
import engine.level.utils.DesignLabel;
import engine.utils.Point;
import engine.utils.components.draw.TextureMap;
import engine.utils.components.path.IPath;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;

/** Wall connectivity and separation of the floor and wall drawing passes. */
class LevelRendererTest {
  @Test
  void isolatedAndSurroundedWallsUseDifferentAtlasCells() {
    Tile[][] layout = floors(3, 3);
    layout[1][1] = wall(1, 1);
    assertEquals(36, LevelRenderer.wallCell(layout, 1, 1));
    for (int y = 0; y < 3; y++) for (int x = 0; x < 3; x++) layout[y][x] = wall(x, y);
    assertEquals(33, LevelRenderer.wallCell(layout, 1, 1));
    assertEquals(33, LevelRenderer.wallCell(new Tile[][] {{wall(0, 0)}}, 0, 0));
  }

  @Test
  void diagonalsOnlyJoinWhenBothAdjacentSidesConnect() {
    Tile[][] layout = floors(3, 3);
    layout[1][1] = wall(1, 1);
    layout[2][2] = wall(2, 2);
    assertEquals(36, LevelRenderer.wallCell(layout, 1, 1));
    layout[2][1] = wall(1, 2);
    layout[1][2] = wall(2, 1);
    assertEquals(44, LevelRenderer.wallCell(layout, 1, 1));
    layout[2][2] = floor(2, 2);
    assertEquals(25, LevelRenderer.wallCell(layout, 1, 1));
  }

  @ParameterizedTest
  @CsvSource({
    "0,1,24", "1,0,37", "0,-1,0", "-1,0,39", "1,1,44", "1,-1,8", "-1,-1,11", "-1,1,47"
  })
  void directionalConnectionsUseFixedAtlasCells(int dx, int dy, int expectedCell) {
    Tile[][] layout = floors(3, 3);
    layout[1][1] = wall(1, 1);
    if (dx != 0) layout[1][1 + dx] = wall(1 + dx, 1);
    if (dy != 0) layout[1 + dy][1] = wall(1, 1 + dy);
    if (dx != 0 && dy != 0) layout[1 + dy][1 + dx] = wall(1 + dx, 1 + dy);
    // Fixed indices in the 12x4 art atlas, independent of ATLAS_MASKS and its inverse.
    assertEquals(expectedCell, LevelRenderer.wallCell(layout, 1, 1));
  }

  @Test
  void regularDoorsJoinWallsAndHiddenDoorsChangeTheirDrawingPass() {
    Tile[][] layout = floors(3, 3);
    DoorTile door = new DoorTile(new Coordinate(2, 1), DesignLabel.DEFAULT);
    layout[1][2] = door;
    assertEquals(37, LevelRenderer.wallCell(layout, 1, 1));
    assertFalse(LevelRenderer.isAutoWall(door));
    door.hidden(true);
    assertFalse(LevelRenderer.isAutoWall(door));
    door.close();
    assertTrue(LevelRenderer.isAutoWall(door));
    assertTrue(LevelRenderer.isAutoWall(wall(0, 0)));
    assertFalse(LevelRenderer.isAutoWall(floor(0, 0)));
  }

  @Test
  void tileLookupHandlesRaggedLayoutsAndAllOutsideEdges() {
    Tile tile = floor(0, 0);
    Tile[][] layout = {{tile}, {}};
    assertEquals(tile, LevelRenderer.at(layout, 0, 0));
    assertNull(LevelRenderer.at(layout, -1, 0));
    assertNull(LevelRenderer.at(layout, 0, -1));
    assertNull(LevelRenderer.at(layout, 1, 0));
    assertNull(LevelRenderer.at(layout, 0, 1));
    assertNull(LevelRenderer.at(layout, 0, 2));
  }

  @Test
  void floorsAndWallsAreDrawnInTheirOwnPassAndInvisibleTilesAreSkipped() {
    withTextures(
        false,
        (renderer, batch, texture) -> {
          Tile[][] layout = floors(3, 3);
          layout[1][1] = wall(1, 1);
          layout[0][0].visible(false);
          layout[1][0].visible(false);
          renderer.drawFloor(batch, layout, 1, 1, 1, 1);
          verify(batch, never())
              .draw(any(Texture.class), anyFloat(), anyFloat(), anyFloat(), anyFloat());
          verify(batch, never())
              .draw(
                  any(Texture.class),
                  anyFloat(),
                  anyFloat(),
                  anyFloat(),
                  anyFloat(),
                  anyInt(),
                  anyInt(),
                  anyInt(),
                  anyInt(),
                  anyBoolean(),
                  anyBoolean());
          renderer.drawWalls(batch, layout, 1, 1, 1, 1, 0, false);
          verify(batch).draw(texture, 1, 1, 1, 1, 0, 48, 16, 16, false, false);
          renderer.drawFloor(batch, layout, 2, 2, 2, 2);
          verify(batch).draw(texture, 2, 2, 1, 1);
          renderer.drawFloor(batch, layout, 0, 0, 0, 0);
          verify(batch, never()).draw(texture, 0, 0, 1, 1);
          verify(batch, atLeastOnce()).setColor(Color.WHITE);
        });
  }

  @Test
  void atlasFloorBesideWallsUsesTheMeasuredGroundRectangle() {
    withTextures(
        true,
        (renderer, batch, texture) -> {
          Tile[][] layout = floors(3, 3);
          layout[1][1] = wall(1, 1);
          renderer.drawFloor(batch, layout, 1, 1, 1, 1);
          verify(batch)
              .draw(
                  eq(texture),
                  eq(1f),
                  eq(1f),
                  eq(1f),
                  eq(0.25f),
                  anyInt(),
                  anyInt(),
                  eq(16),
                  eq(4),
                  eq(false),
                  eq(false));
        });
  }

  @Test
  void wallDecorationAddsTorchLightAndFlameOnlyOnVisibleWallFaces() {
    withTextures(
        false,
        (renderer, batch, texture) -> {
          Tile[][] layout = floors(5, 3);
          for (int x = 0; x < 5; x++) layout[2][x] = wall(x, 2);
          List<Light> lights = new ArrayList<>();
          List<Point> flames = new ArrayList<>();
          renderer.collectLights(layout, 0, 4, 2, 2, lights, flames);
          assertEquals(2, flames.size());
          assertEquals(List.of(new Point(0.5f, 3.0625f), new Point(4.5f, 3.0625f)), flames);
          assertTrue(
              lights.stream()
                  .anyMatch(
                      light ->
                          light.position().equals(new Point(0.5f, 1.85f))
                              && light.radius() == 6.5f));
          renderer.drawWalls(batch, layout, 0, 4, 2, 2, 1, true);
          layout[2][0].visible(false);
          lights.clear();
          flames.clear();
          renderer.collectLights(layout, 0, 0, 2, 2, lights, flames);
          assertTrue(lights.isEmpty());
          assertTrue(flames.isEmpty());
        });
  }

  /** A drawing assertion with isolated texture dependencies. */
  private interface DrawingCheck {
    void check(LevelRenderer renderer, SpriteBatch batch, Texture texture);
  }

  private static void withTextures(boolean variants, DrawingCheck check) {
    Texture texture = mock(Texture.class);
    when(texture.getWidth()).thenReturn(192);
    TextureMap map = mock(TextureMap.class);
    when(map.textureAt(any(IPath.class))).thenReturn(texture);
    float[][] insets = new float[48][3];
    int[][] rects = new int[48][];
    for (int i = 0; i < 48; i++) rects[i] = new int[] {0, 12, 16, 4};
    DesignTileset tileset =
        new DesignTileset(
            TileTextures.wallAtlas(DesignLabel.DEFAULT),
            variants
                ? Optional.of(TileTextures.path(DesignLabel.DEFAULT, "floor/variants.png"))
                : Optional.empty(),
            true,
            insets,
            rects);
    try (MockedStatic<TextureMap> textures = mockStatic(TextureMap.class);
        MockedStatic<DesignTileset> sets = mockStatic(DesignTileset.class);
        MockedStatic<RenderTextures> helpers = mockStatic(RenderTextures.class)) {
      textures.when(TextureMap::instance).thenReturn(map);
      sets.when(() -> DesignTileset.of(DesignLabel.DEFAULT)).thenReturn(tileset);
      Texture helperTexture = mock(Texture.class);
      helpers.when(RenderTextures::wallShadowSide).thenReturn(helperTexture);
      helpers.when(RenderTextures::wallShadowTop).thenReturn(helperTexture);
      helpers.when(RenderTextures::abyssFade).thenReturn(helperTexture);
      check.check(new LevelRenderer(), mock(SpriteBatch.class), texture);
    }
  }

  private static Tile[][] floors(int width, int height) {
    Tile[][] layout = new Tile[height][width];
    for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) layout[y][x] = floor(x, y);
    return layout;
  }

  private static Tile floor(int x, int y) {
    return new FloorTile(new Coordinate(x, y), DesignLabel.DEFAULT);
  }

  private static Tile wall(int x, int y) {
    return new WallTile(new Coordinate(x, y), DesignLabel.DEFAULT);
  }
}
