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
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import engine.level.Tile;
import engine.level.elements.tile.DoorTile;
import engine.level.elements.tile.ExitTile;
import engine.level.elements.tile.FloorTile;
import engine.level.elements.tile.HoleTile;
import engine.level.elements.tile.SkipTile;
import engine.level.elements.tile.WallTile;
import engine.level.utils.Coordinate;
import engine.level.utils.DesignLabel;
import engine.utils.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Wall connectivity and separation of the floor and wall drawing passes. */
class LevelRendererTest {
  // Clockwise bits: N, NE, E, SE, S, SW, W, NW; north is positive world y.
  private static final int[][] OFFSETS = {
    {0, 1}, {1, 1}, {1, 0}, {1, -1}, {0, -1}, {-1, -1}, {-1, 0}, {-1, 1}
  };

  @Test
  void hiddenDoorsUseTheWallPassOnlyWhenClosed() {
    DoorTile door = new DoorTile(new Coordinate(2, 1), DesignLabel.DEFAULT);
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
    RendererScenarioFixture.run(
        false,
        false,
        fixture -> {
          Tile[][] layout = floors(3, 3);
          layout[1][1] = wall(1, 1);
          layout[0][0].visible(false);
          layout[1][0].visible(false);
          fixture.tiles.drawFloor(fixture.batch, layout, 1, 1, 1, 1);
          verify(fixture.batch, never())
              .draw(any(Texture.class), anyFloat(), anyFloat(), anyFloat(), anyFloat());
          verify(fixture.batch, never())
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
          fixture.tiles.drawWalls(fixture.batch, layout, 1, 1, 1, 1, 0, false);
          verify(fixture.batch)
              .draw(
                  fixture.texture("dungeon/default/wall/walls.png"),
                  1,
                  1,
                  1,
                  1,
                  0,
                  48,
                  16,
                  16,
                  false,
                  false);
          fixture.tiles.drawFloor(fixture.batch, layout, 2, 2, 2, 2);
          verify(fixture.batch)
              .draw(fixture.texture("dungeon/default/floor/floor_1.png"), 2, 2, 1, 1);
          fixture.tiles.drawFloor(fixture.batch, layout, 0, 0, 0, 0);
          verify(fixture.batch, never()).draw(any(Texture.class), eq(0f), eq(0f), eq(1f), eq(1f));
          verify(fixture.batch, atLeastOnce()).setColor(Color.WHITE);
        });
  }

  @Test
  void atlasFloorBesideWallsUsesTheMeasuredGroundRectangle() {
    RendererScenarioFixture.run(
        true,
        false,
        fixture -> {
          Tile[][] layout = floors(3, 3);
          layout[1][1] = wall(1, 1);
          fixture.tiles.drawFloor(fixture.batch, layout, 1, 1, 1, 1);
          verify(fixture.batch)
              .draw(
                  eq(fixture.texture("dungeon/default/floor/variants.png")),
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
    RendererScenarioFixture.run(
        false,
        false,
        fixture -> {
          Tile[][] layout = floors(5, 3);
          for (int x = 0; x < 5; x++) layout[2][x] = wall(x, 2);
          List<Light> lights = new ArrayList<>();
          List<Point> flames = new ArrayList<>();
          fixture.tiles.collectLights(layout, 0, 4, 2, 2, lights, flames);
          assertEquals(2, flames.size());
          assertEquals(List.of(new Point(0.5f, 3.0625f), new Point(4.5f, 3.0625f)), flames);
          assertTrue(
              lights.stream()
                  .anyMatch(
                      light ->
                          light.position().equals(new Point(0.5f, 1.85f))
                              && light.radius() == 6.5f));
          fixture.tiles.drawWalls(fixture.batch, layout, 0, 4, 2, 2, 1, true);
          layout[2][0].visible(false);
          lights.clear();
          flames.clear();
          fixture.tiles.collectLights(layout, 0, 0, 2, 2, lights, flames);
          assertTrue(lights.isEmpty());
          assertTrue(flames.isEmpty());
        });
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

  @ParameterizedTest
  @CsvSource({
    "0,36", "1,24", "4,37", "5,25", "7,44", "16,0", "17,12", "20,1", "21,13", "23,28", "28,8",
    "29,16", "31,20", "64,39", "65,27", "68,38", "69,26", "71,41", "80,3", "81,15", "84,2", "85,14",
    "87,7", "92,5", "93,43", "95,32", "112,11", "113,19", "116,6", "117,40", "119,21", "124,10",
    "125,9", "127,17", "193,47", "197,42", "199,45", "209,31", "213,4", "215,46", "221,34",
    "223,29", "241,35", "245,23", "247,30", "253,18", "255,33"
  })
  void validNeighborhoodsSelectTheirPublishedAtlasCell(int mask, int cell) {
    assertEquals(cell, LevelRenderer.wallCell(layout(mask), 1, 1));
  }

  @ParameterizedTest
  @MethodSource("allMasks")
  void diagonalsWithoutBothAdjacentSidesDoNotChangeTheCell(int mask) {
    int normalized = mask & 0x55;
    if ((mask & 7) == 7) normalized |= 2;
    if ((mask & 28) == 28) normalized |= 8;
    if ((mask & 112) == 112) normalized |= 32;
    if ((mask & 193) == 193) normalized |= 128;
    assertEquals(
        LevelRenderer.wallCell(layout(normalized), 1, 1),
        LevelRenderer.wallCell(layout(mask), 1, 1));
  }

  @Test
  void regularAndHiddenDoorsConnectInBothStates() {
    Tile[][] tiles = layout(0);
    DoorTile door = new DoorTile(new Coordinate(2, 1), DesignLabel.DEFAULT);
    tiles[1][2] = door;
    for (boolean hidden : new boolean[] {false, true}) {
      door.hidden(hidden);
      door.open();
      assertEquals(37, LevelRenderer.wallCell(tiles, 1, 1));
      door.close();
      assertEquals(37, LevelRenderer.wallCell(tiles, 1, 1));
    }
  }

  @ParameterizedTest
  @CsvSource({"0,0,11", "1,0,0", "2,0,8", "0,1,39", "2,1,37", "0,2,47", "1,2,24", "2,2,44"})
  void outsideNeighborsConnectAtEachEdgeAndCorner(int x, int y, int cell) {
    Tile[][] tiles = floors(3, 3);
    tiles[y][x] = wall(x, y);
    assertEquals(cell, LevelRenderer.wallCell(tiles, x, y));
  }

  private static IntStream allMasks() {
    return IntStream.range(0, 256);
  }

  private static Tile[][] layout(int mask) {
    Tile[][] tiles = floors(3, 3);
    tiles[1][1] = wall(1, 1);
    for (int bit = 0; bit < 8; bit++) {
      if ((mask & (1 << bit)) == 0) continue;
      int x = 1 + OFFSETS[bit][0];
      int y = 1 + OFFSETS[bit][1];
      tiles[y][x] = wall(x, y);
    }
    return tiles;
  }

  @Test
  void singleWallConnectsToAllOutsideNeighbors() {
    assertEquals(33, LevelRenderer.wallCell(new Tile[][] {{wall(0, 0)}}, 0, 0));
  }

  @ParameterizedTest
  @CsvSource({"2,3,top", "1,2,right", "3,2,left"})
  void floorShadowsTouchTheMeasuredWallArt(int wallX, int wallY, String edge) {
    RendererScenarioFixture.run(
        true,
        true,
        fixture -> {
          Tile[][] tiles = RendererScenarioFixture.floors(5, 5);
          tiles[wallY][wallX] = RendererScenarioFixture.wall(wallX, wallY);
          fixture.tiles.drawFloor(fixture.batch, tiles, 2, 2, 2, 2);
          switch (edge) {
            case "top" -> verify(fixture.batch).draw(fixture.texture("shadowTop"), 2, 2.25f, 1, 1);
            case "right" ->
                verify(fixture.batch).draw(fixture.texture("shadowSide"), 1.8125f, 2, 1, 1);
            case "left" ->
                verify(fixture.batch)
                    .draw(
                        fixture.texture("shadowSide"), 2.125f, 2, 1, 1, 0, 0, 16, 16, true, false);
            default -> throw new AssertionError(edge);
          }
        });
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void wallsOutsideTheLayoutHaveNoArtInset(boolean variants) {
    RendererScenarioFixture.run(
        variants,
        true,
        fixture -> {
          fixture.tiles.drawFloor(fixture.batch, RendererScenarioFixture.floors(1, 1), 0, 0, 0, 0);
          verify(fixture.batch).draw(fixture.texture("shadowTop"), 0, 0, 1, 1);
          verify(fixture.batch).draw(fixture.texture("shadowSide"), 0, 0, 1, 1);
          verify(fixture.batch)
              .draw(fixture.texture("shadowSide"), 0, 0, 1, 1, 0, 0, 16, 16, true, false);
        });
  }

  @ParameterizedTest
  @CsvSource({"1,1,0,false", "3,0,2,false", "5,1,4,false", "25,2,3,true"})
  void floorVariantsAndSkullOverlaysStayStableAtFixedCoordinates(
      int x, int y, int cell, boolean skull) {
    RendererScenarioFixture.run(
        true,
        true,
        fixture -> {
          Tile[][] tiles = RendererScenarioFixture.floors(x + 2, y + 2);
          Texture atlas = fixture.texture("dungeon/default/floor/variants.png");
          fixture.tiles.drawFloor(fixture.batch, tiles, x, x, y, y);
          verify(fixture.batch).draw(atlas, x, y, 1, 1, cell * 16, 0, 16, 16, false, false);
          verify(fixture.batch, skull ? org.mockito.Mockito.times(1) : never())
              .draw(atlas, x, y, 1, 1, 128, 0, 16, 16, false, false);
          clearInvocations(fixture.batch);
          fixture.tiles.drawFloor(fixture.batch, tiles, x, x, y, y);
          verify(fixture.batch).draw(atlas, x, y, 1, 1, cell * 16, 0, 16, 16, false, false);
        });
  }

  @ParameterizedTest
  @CsvSource({
    "true,false,false",
    "true,true,false",
    "false,false,false",
    "false,true,false",
    "true,false,true",
    "true,true,true",
    "false,false,true",
    "false,true,true"
  })
  void doorsMoveBetweenFloorAndWallPasses(boolean variants, boolean open, boolean hidden) {
    RendererScenarioFixture.run(
        variants,
        true,
        fixture -> {
          Tile[][] tiles = RendererScenarioFixture.floors(3, 3);
          DoorTile door = new DoorTile(new Coordinate(1, 1), DesignLabel.DEFAULT);
          door.hidden(hidden);
          if (!open) door.close();
          tiles[1][1] = door;
          boolean floor = variants && open;
          fixture.tiles.drawFloor(fixture.batch, tiles, 1, 1, 1, 1);
          if (floor) {
            verify(fixture.batch)
                .draw(
                    eq(fixture.texture("dungeon/default/floor/variants.png")),
                    eq(1f),
                    eq(1f),
                    eq(1f),
                    eq(1f),
                    anyInt(),
                    anyInt(),
                    eq(16),
                    eq(16),
                    eq(false),
                    eq(false));
          } else if (hidden && !open && variants) {
            verify(fixture.batch)
                .draw(
                    eq(fixture.texture("dungeon/default/floor/variants.png")),
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
          } else {
            noDraws(fixture);
          }
          clearInvocations(fixture.batch);
          fixture.tiles.drawWalls(fixture.batch, tiles, 1, 1, 1, 1, 0, false);
          if (hidden && !open) {
            verify(fixture.batch)
                .draw(
                    eq(fixture.texture("dungeon/default/wall/walls.png")),
                    eq(1f),
                    eq(1f),
                    eq(1f),
                    eq(1f),
                    anyInt(),
                    anyInt(),
                    eq(16),
                    eq(16),
                    eq(false),
                    eq(false));
          } else if (!floor) {
            String name =
                hidden ? "floor/floor_1.png" : "door/top" + (open ? "" : "_closed") + ".png";
            verify(fixture.batch).draw(fixture.texture("dungeon/default/" + name), 1, 1, 1, 1);
          } else noDraws(fixture);
        });
  }

  @ParameterizedTest
  @CsvSource({
    "false,FLOOR,false",
    "true,FLOOR,true",
    "true,WALL,true",
    "true,EXIT,false",
    "true,HOLE,false"
  })
  void tintUsesPremultipliedAlphaAndOnlyAtlasTilesFadeTheirTint(
      boolean variants, String kind, boolean faded) {
    RendererScenarioFixture.run(
        variants,
        true,
        fixture -> {
          Tile[][] tiles = RendererScenarioFixture.floors(3, 3);
          Tile tile =
              switch (kind) {
                case "WALL" -> RendererScenarioFixture.wall(1, 1);
                case "EXIT" -> new ExitTile(new Coordinate(1, 1), DesignLabel.DEFAULT);
                case "HOLE" -> new HoleTile(new Coordinate(1, 1), DesignLabel.DEFAULT);
                default -> tiles[1][1];
              };
          tile.tintColor(0x33669980);
          tiles[1][1] = tile;
          fixture.tiles.drawFloor(fixture.batch, tiles, 1, 1, 1, 1);
          Color color = fixture.colors.getFirst();
          float alpha = 128 / 255f;
          assertEquals((faded ? 0.7333333f : 0.2f) * alpha, color.r, 0.000001f);
          assertEquals((faded ? 0.8f : 0.4f) * alpha, color.g, 0.000001f);
          assertEquals((faded ? 0.8666667f : 0.6f) * alpha, color.b, 0.000001f);
          assertEquals(alpha, color.a);
          assertEquals(Color.WHITE, fixture.colors.getLast());
        });
  }

  @ParameterizedTest
  @CsvSource({"1,1,18", "1,-1,30", "-1,-1,29", "-1,1,17"})
  void aVoidCornerJoinsTwoVisibleWallsAroundADiagonalFloor(int dx, int dy, int cell) {
    RendererScenarioFixture.run(
        false,
        true,
        fixture -> {
          Tile[][] tiles = new Tile[5][5];
          tiles[2][2 + dx] = RendererScenarioFixture.wall(2 + dx, 2);
          tiles[2 + dy][2] = RendererScenarioFixture.wall(2, 2 + dy);
          tiles[2 + dy][2 + dx] = RendererScenarioFixture.floors(5, 5)[2 + dy][2 + dx];
          fixture.tiles.drawWalls(fixture.batch, tiles, 2, 2, 2, 2, 0, false);
          verify(fixture.batch)
              .draw(
                  fixture.texture("dungeon/default/wall/walls.png"),
                  2,
                  2,
                  1,
                  1,
                  cell % 12 * 16,
                  cell / 12 * 16,
                  16,
                  16,
                  false,
                  false);
        });
  }

  @ParameterizedTest
  @ValueSource(strings = {"missingWall", "hiddenWall", "hiddenFloor", "diagonalWall", "floorSide"})
  void incompleteOrHiddenNeighborsDoNotInventVoidCorners(String scenario) {
    RendererScenarioFixture.run(
        false,
        true,
        fixture -> {
          Tile[][] tiles = new Tile[5][5];
          tiles[2][3] = RendererScenarioFixture.wall(3, 2);
          tiles[3][2] = RendererScenarioFixture.wall(2, 3);
          tiles[3][3] = RendererScenarioFixture.floors(5, 5)[3][3];
          switch (scenario) {
            case "missingWall" -> tiles[3][2] = null;
            case "hiddenWall" -> tiles[2][3].visible(false);
            case "hiddenFloor" -> tiles[3][3].visible(false);
            case "diagonalWall" -> tiles[3][3] = RendererScenarioFixture.wall(3, 3);
            case "floorSide" -> tiles[3][2] = RendererScenarioFixture.floors(5, 5)[3][2];
            default -> throw new AssertionError(scenario);
          }
          fixture.tiles.drawWalls(fixture.batch, tiles, 2, 2, 2, 2, 0, false);
          noDraws(fixture);
        });
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void abyssEdgesContinueTheFloorAndThenApplyTheFade(boolean variants) {
    RendererScenarioFixture.run(
        variants,
        true,
        fixture -> {
          Tile[][] tiles = RendererScenarioFixture.floors(3, 3);
          tiles[0][1] = new SkipTile(new Coordinate(1, 0), DesignLabel.DEFAULT);
          fixture.tiles.drawFloor(fixture.batch, tiles, 1, 1, 0, 0);
          if (variants)
            verify(fixture.batch)
                .draw(
                    eq(fixture.texture("dungeon/default/floor/variants.png")),
                    eq(1f),
                    eq(0f),
                    eq(1f),
                    eq(1f),
                    anyInt(),
                    anyInt(),
                    eq(16),
                    eq(16),
                    eq(false),
                    eq(false));
          else
            verify(fixture.batch)
                .draw(fixture.texture("dungeon/default/floor/floor_1.png"), 1, 0, 1, 1);
          verify(fixture.batch).draw(fixture.texture("abyss"), 1, 0, 1, 1);
        });
  }

  @ParameterizedTest
  @ValueSource(strings = {"transparent", "invisible", "wall", "hole", "missing"})
  void hiddenOrInaccessibleGroundDoesNotCreateAnAbyssEdge(String scenario) {
    RendererScenarioFixture.run(
        true,
        true,
        fixture -> {
          Tile[][] tiles = RendererScenarioFixture.floors(3, 3);
          tiles[0][1] = null;
          switch (scenario) {
            case "transparent" -> tiles[1][1].tintColor(0xffffff00);
            case "invisible" -> tiles[1][1].visible(false);
            case "wall" -> tiles[1][1] = RendererScenarioFixture.wall(1, 1);
            case "hole" -> tiles[1][1] = new HoleTile(new Coordinate(1, 1), DesignLabel.DEFAULT);
            case "missing" -> tiles[1][1] = null;
            default -> throw new AssertionError(scenario);
          }
          fixture.tiles.drawFloor(fixture.batch, tiles, 1, 1, 0, 0);
          noDraws(fixture);
        });
  }

  @ParameterizedTest
  @CsvSource({"30,false,5,3", "45,true,2,0"})
  void waterAndLavaFountainsAnimateAndEmitDifferentLight(
      int x, boolean lava, int firstCell, int nextCell) {
    RendererScenarioFixture.run(
        false,
        true,
        fixture -> {
          Tile[][] tiles = wallFace();
          List<Light> lights = new ArrayList<>();
          List<Point> flames = new ArrayList<>();
          fixture.tiles.collectLights(tiles, x, x, 2, 2, lights, flames);
          assertEquals(1, lights.size());
          Light light = lights.getFirst();
          assertEquals(new Point(x + 0.5f, 1.85f), light.position());
          assertEquals(
              lava ? new Color(1, 0.36f, 0.1f, 1) : new Color(0.3f, 0.62f, 1, 1), light.color());
          assertEquals(4.5f, light.radius());
          assertEquals(0.85f, light.intensity());
          assertEquals(lava ? 0.1f : 0, light.flicker());
          assertTrue(flames.isEmpty());
          Texture atlas = fixture.texture("dungeon/default/wall/fountain.png");
          fixture.tiles.drawWalls(fixture.batch, tiles, x, x, 2, 2, 0, true);
          verify(fixture.batch).draw(atlas, x, 2, 1, 1, firstCell * 16, 0, 16, 16, false, false);
          clearInvocations(fixture.batch);
          fixture.tiles.drawWalls(fixture.batch, tiles, x, x, 2, 2, 1 / 6f, true);
          verify(fixture.batch).draw(atlas, x, 2, 1, 1, nextCell * 16, 0, 16, 16, false, false);
        });
  }

  @ParameterizedTest
  @CsvSource({"2,5", "11,6", "21,4", "34,2"})
  void wallBannersAndCracksUseStableAtlasCells(int x, int cell) {
    RendererScenarioFixture.run(
        false,
        true,
        fixture -> {
          fixture.tiles.drawWalls(fixture.batch, wallFace(), x, x, 2, 2, 0, true);
          verify(fixture.batch)
              .draw(
                  fixture.texture("dungeon/default/wall/decor.png"),
                  x,
                  2,
                  1,
                  1,
                  cell * 16,
                  0,
                  16,
                  16,
                  false,
                  false);
        });
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "invisible",
        "transparent",
        "openSide",
        "blockedBelow",
        "missingArt",
        "noBelow",
        "missingWall",
        "floor"
      })
  void wallDecorationNeedsAnExposedVisibleFace(String scenario) {
    RendererScenarioFixture.run(
        false,
        true,
        fixture -> {
          Tile[][] tiles = wallFace();
          switch (scenario) {
            case "invisible" -> tiles[2][0].visible(false);
            case "transparent" -> tiles[2][0].tintColor(0xffffff00);
            case "openSide" -> tiles[2][1] = RendererScenarioFixture.floors(3, 3)[2][1];
            case "blockedBelow" -> tiles[1][0] = RendererScenarioFixture.wall(0, 1);
            case "missingArt" -> fixture.decor = false;
            case "noBelow" -> tiles[1][0] = null;
            case "missingWall" -> tiles[2][0] = null;
            case "floor" -> tiles[2][0] = RendererScenarioFixture.floors(3, 3)[2][0];
            default -> throw new AssertionError(scenario);
          }
          List<Light> lights = new ArrayList<>();
          List<Point> flames = new ArrayList<>();
          fixture.tiles.collectLights(tiles, 0, 0, 2, 2, lights, flames);
          assertTrue(lights.isEmpty());
          assertTrue(flames.isEmpty());
          fixture.tiles.drawWalls(fixture.batch, tiles, 0, 0, 2, 2, 0, true);
          Texture torch = fixture.texture("dungeon/default/wall/torch.png");
          verify(fixture.batch, never())
              .draw(
                  eq(torch),
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
        });
  }

  private static Tile[][] wallFace() {
    Tile[][] tiles = RendererScenarioFixture.floors(48, 4);
    for (int x = 0; x < 48; x++) tiles[2][x] = RendererScenarioFixture.wall(x, 2);
    return tiles;
  }

  private static void noDraws(RendererScenarioFixture fixture) {
    verify(fixture.batch, never())
        .draw(any(Texture.class), anyFloat(), anyFloat(), anyFloat(), anyFloat());
    verify(fixture.batch, never())
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
  }
}
