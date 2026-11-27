package testingUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import engine.Game;
import engine.level.DungeonLevel;
import engine.level.utils.Coordinate;
import engine.level.utils.LevelElement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TestLevelsTest {

  @AfterEach
  void unloadLevel() {
    TestLevels.unload();
  }

  private static LevelElement elementAt(DungeonLevel level, int x, int y) {
    return level.tileAt(new Coordinate(x, y)).orElseThrow().levelElement();
  }

  @Test
  void loadedLevelBecomesTheCurrentLevel() {
    DungeonLevel level = TestLevels.load("empty_room");
    assertSame(level, Game.currentLevel().orElseThrow());
  }

  @Test
  void emptyRoomHasFloorInsideAndWallsAround() {
    DungeonLevel level = TestLevels.load("empty_room");
    assertEquals(9, level.floorTiles().size());
    assertEquals(16, level.wallTiles().size());
    assertEquals(new Coordinate(2, 2), level.startTile().orElseThrow().coordinate());
  }

  @Test
  void obstacleRoomHasAWallInTheMiddle() {
    DungeonLevel level = TestLevels.load("obstacle_room");
    assertEquals(LevelElement.WALL, elementAt(level, 3, 2));
    assertEquals(LevelElement.FLOOR, elementAt(level, 2, 2));
  }

  @Test
  void exitRoomHasAnExitInTheMiddle() {
    DungeonLevel level = TestLevels.load("exit_room");
    assertEquals(new Coordinate(2, 2), level.endTile().orElseThrow().coordinate());
  }

  @Test
  void unknownLevelIsRejected() {
    assertThrows(IllegalArgumentException.class, () -> TestLevels.load("missing_room"));
  }

  @Test
  void firstAsciiRowIsTheTopOfTheLevel() {
    DungeonLevel level = TestLevels.fromAscii("WW", "FW");
    assertEquals(LevelElement.FLOOR, elementAt(level, 0, 0));
    assertEquals(LevelElement.WALL, elementAt(level, 0, 1));
  }

  @Test
  void unloadRemovesTheLevel() {
    TestLevels.load("empty_room");
    TestLevels.unload();
    assertTrue(Game.currentLevel().isEmpty());
  }
}
