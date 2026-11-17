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

  @ParameterizedTest
  @CsvSource({"1,0,top", "0,1,right", "2,1,left", "1,2,bottom", "-1,-1,top"})
  void doorsFaceTheRoomInBothOpenAndClosedStates(int floorX, int floorY, String side) {
    Tile[][] layout = new Tile[3][3];
    DoorTile door = new DoorTile(new Coordinate(1, 1), DesignLabel.ICE);
    layout[1][1] = door;
    if (floorX >= 0)
      layout[floorY][floorX] = new FloorTile(new Coordinate(floorX, floorY), DesignLabel.ICE);
    assertEquals(
        "dungeon/ice/door/" + side + ".png", TileTextures.texture(door, layout).pathString());
    door.close();
    assertEquals(
        "dungeon/ice/door/" + side + "_closed.png",
        TileTextures.texture(door, layout).pathString());
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
}
