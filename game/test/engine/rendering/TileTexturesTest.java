package engine.rendering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import engine.level.Tile;
import engine.level.elements.tile.DoorTile;
import engine.level.elements.tile.FloorTile;
import engine.level.utils.Coordinate;
import engine.level.utils.DesignLabel;
import engine.level.utils.LevelElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/** Texture selection contracts for previews and doors. */
class TileTexturesTest {
  @ParameterizedTest
  @CsvSource({
    "FLOOR,floor/floor_1.png",
    "HOLE,floor/floor_hole.png",
    "EXIT,floor/floor_ladder.png",
    "DOOR,door/top.png",
    "WALL,wall/walls.png"
  })
  void previewsUseTheSelectedDesign(LevelElement element, String suffix) {
    for (DesignLabel design : DesignLabel.values()) {
      assertEquals(
          "dungeon/" + design.name().toLowerCase() + "/" + suffix,
          TileTextures.preview(element, design).pathString());
    }
  }

  @Test
  void voidPreviewAndCachedPathsAreStable() {
    assertEquals(
        "hud/cross.png", TileTextures.preview(LevelElement.SKIP, DesignLabel.DEFAULT).pathString());
    assertSame(
        TileTextures.wallAtlas(DesignLabel.DEFAULT), TileTextures.wallAtlas(DesignLabel.DEFAULT));
  }

  @Test
  void hiddenDoorsBecomeFloorWhenOpened() {
    DoorTile door = new DoorTile(new Coordinate(0, 0), DesignLabel.DEFAULT);
    door.hidden(true);
    Tile[][] layout = {{door}};
    door.close();
    assertEquals(TileTextures.wallAtlas(DesignLabel.DEFAULT), TileTextures.texture(door, layout));
    door.open();
    assertEquals(
        TileTextures.preview(LevelElement.FLOOR, DesignLabel.DEFAULT),
        TileTextures.texture(door, layout));
    FloorTile floor = new FloorTile(new Coordinate(0, 0), DesignLabel.DEFAULT);
    assertEquals(
        TileTextures.preview(LevelElement.FLOOR, DesignLabel.DEFAULT),
        TileTextures.texture(floor, layout));
  }

  @ParameterizedTest
  @CsvSource({
    "3,3,1,1,1,0,top",
    "3,3,1,1,0,1,right",
    "3,3,1,1,2,1,left",
    "3,3,1,1,1,2,bottom",
    "3,3,1,1,-1,-1,top",
    "3,3,0,1,1,1,left",
    "3,3,2,1,1,1,right",
    "3,3,1,0,1,1,bottom",
    "3,3,1,2,1,1,top",
    "3,1,0,0,1,0,left",
    "3,1,2,0,1,0,right",
    "1,3,0,0,0,1,bottom",
    "1,3,0,2,0,1,top",
    "1,1,0,0,-1,-1,top"
  })
  void regularDoorsFaceTheRoomAcrossLayoutShapes(
      int width, int height, int x, int y, int floorX, int floorY, String side) {
    Tile[][] layout = new Tile[height][width];
    DoorTile door = new DoorTile(new Coordinate(x, y), DesignLabel.ICE);
    layout[y][x] = door;
    if (floorX >= 0)
      layout[floorY][floorX] = new FloorTile(new Coordinate(floorX, floorY), DesignLabel.ICE);
    assertBothStates(door, layout, side);
  }

  @ParameterizedTest
  @CsvSource({"15,top", "14,right", "12,left", "8,bottom", "0,top"})
  void multipleRoomNeighborsUseBottomThenLeftThenRightThenTop(int neighbors, String side) {
    Tile[][] layout = new Tile[3][3];
    DoorTile door = new DoorTile(new Coordinate(1, 1), DesignLabel.ICE);
    layout[1][1] = door;
    int[][] offsets = {{0, -1}, {-1, 0}, {1, 0}, {0, 1}};
    for (int i = 0; i < offsets.length; i++) {
      if ((neighbors & (1 << i)) == 0) continue;
      int x = 1 + offsets[i][0];
      int y = 1 + offsets[i][1];
      layout[y][x] = new FloorTile(new Coordinate(x, y), DesignLabel.ICE);
    }
    assertBothStates(door, layout, side);
  }

  @ParameterizedTest
  @EnumSource(DesignLabel.class)
  void raggedLayoutsKeepTheDoorDesignInBothStates(DesignLabel design) {
    DoorTile door = new DoorTile(new Coordinate(1, 1), design);
    Tile[][] layout = {{}, {new FloorTile(new Coordinate(0, 1), design), door}, {}};
    assertBothStates(door, layout, "right");
  }

  private static void assertBothStates(DoorTile door, Tile[][] layout, String side) {
    String prefix = "dungeon/" + door.designLabel().name().toLowerCase() + "/door/" + side;
    door.open();
    assertEquals(prefix + ".png", TileTextures.texture(door, layout).pathString());
    door.close();
    assertEquals(prefix + "_closed.png", TileTextures.texture(door, layout).pathString());
  }
}
