package engine.rendering;

import static org.junit.jupiter.api.Assertions.assertEquals;

import engine.level.Tile;
import engine.level.elements.tile.DoorTile;
import engine.level.elements.tile.FloorTile;
import engine.level.utils.Coordinate;
import engine.level.utils.DesignLabel;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/** Door orientation at boundaries and when several room-facing sides are available. */
class DoorTextureBoundaryTest {
  @ParameterizedTest
  @CsvSource({
    "3,3,0,1,1,1,left", "3,3,2,1,1,1,right",
    "3,3,1,0,1,1,bottom", "3,3,1,2,1,1,top",
    "3,1,0,0,1,0,left", "3,1,2,0,1,0,right",
    "1,3,0,0,0,1,bottom", "1,3,0,2,0,1,top",
    "1,1,0,0,-1,-1,top"
  })
  void regularDoorsHandleEdgesAndNarrowLayouts(
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
