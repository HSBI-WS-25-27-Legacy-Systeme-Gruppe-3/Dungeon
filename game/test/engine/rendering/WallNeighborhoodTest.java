package engine.rendering;

import static org.junit.jupiter.api.Assertions.assertEquals;

import engine.level.Tile;
import engine.level.elements.tile.DoorTile;
import engine.level.elements.tile.FloorTile;
import engine.level.elements.tile.WallTile;
import engine.level.utils.Coordinate;
import engine.level.utils.DesignLabel;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

/** Fixed art-atlas expectations, independent of the production lookup tables. */
class WallNeighborhoodTest {
  // Clockwise bits: N, NE, E, SE, S, SW, W, NW; north is positive world y.
  private static final int[][] OFFSETS = {
    {0, 1}, {1, 1}, {1, 0}, {1, -1}, {0, -1}, {-1, -1}, {-1, 0}, {-1, 1}
  };

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
    Tile[][] tiles = layout(0);
    tiles[1][1] = new FloorTile(new Coordinate(1, 1), DesignLabel.DEFAULT);
    tiles[y][x] = new WallTile(new Coordinate(x, y), DesignLabel.DEFAULT);
    assertEquals(cell, LevelRenderer.wallCell(tiles, x, y));
  }

  private static IntStream allMasks() {
    return IntStream.range(0, 256);
  }

  private static Tile[][] layout(int mask) {
    Tile[][] tiles = new Tile[3][3];
    for (int y = 0; y < 3; y++)
      for (int x = 0; x < 3; x++)
        tiles[y][x] = new FloorTile(new Coordinate(x, y), DesignLabel.DEFAULT);
    tiles[1][1] = new WallTile(new Coordinate(1, 1), DesignLabel.DEFAULT);
    for (int bit = 0; bit < 8; bit++) {
      if ((mask & (1 << bit)) == 0) continue;
      int x = 1 + OFFSETS[bit][0];
      int y = 1 + OFFSETS[bit][1];
      tiles[y][x] = new WallTile(new Coordinate(x, y), DesignLabel.DEFAULT);
    }
    return tiles;
  }
}
