package engine.rendering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.graphics.Color;
import engine.level.Tile;
import engine.level.elements.tile.FloorTile;
import engine.level.elements.tile.WallTile;
import engine.level.utils.Coordinate;
import engine.level.utils.DesignLabel;
import engine.utils.Point;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests the placement and switching of ceiling lamps. */
class CeilingLightsTest {
  @Test
  void fullBlocksPlaceLampsAtTheirCentersAndRespectSwitches() {
    Tile[][] layout = new Tile[4][8];
    for (int y = 0; y < 4; y++) for (int x = 0; x < 8; x++) layout[y][x] = floor(x, y);
    List<Light> lights = new ArrayList<>();
    CeilingLights.add(layout, Color.CYAN, 0.8f, (x, y) -> x < 4, lights);
    assertEquals(List.of(Light.steady(new Point(2.5f, 2.5f), Color.CYAN, 0.8f)), lights);
  }

  @Test
  void narrowRaggedRoomsUseTheNearestFloorAndSkipEmptyBlocks() {
    Tile[][] layout = {new Tile[5], new Tile[1]};
    layout[0][0] = new WallTile(new Coordinate(0, 0), DesignLabel.DEFAULT);
    layout[0][1] = floor(1, 0);
    layout[1][0] = floor(0, 1);
    List<Light> lights = new ArrayList<>();
    CeilingLights.add(layout, Color.WHITE, 1, (x, y) -> true, lights);
    assertEquals(List.of(Light.steady(new Point(1.5f, 0.5f), Color.WHITE, 1)), lights);
  }

  @Test
  void movedLampsDoNotCrowdAnExistingLamp() {
    Tile[][] layout = new Tile[4][8];
    layout[2][2] = floor(2, 2);
    layout[2][4] = floor(4, 2);
    List<Light> lights = new ArrayList<>();
    CeilingLights.add(layout, Color.WHITE, 1, (x, y) -> true, lights);
    assertEquals(1, lights.size());
    assertEquals(new Point(2.5f, 2.5f), lights.getFirst().position());
  }

  @Test
  void emptyLayoutHasNoLights() {
    List<Light> lights = new ArrayList<>();
    CeilingLights.add(new Tile[0][], Color.WHITE, 1, (x, y) -> true, lights);
    assertTrue(lights.isEmpty());
  }

  private static Tile floor(int x, int y) {
    return new FloorTile(new Coordinate(x, y), DesignLabel.DEFAULT);
  }
}
