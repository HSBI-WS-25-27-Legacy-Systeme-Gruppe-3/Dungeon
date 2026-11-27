package engine.level.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import engine.utils.Point;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import testingUtils.TestLevels;

/** Tests for the {@link LevelUtils} class. */
public class LevelUtilsTest {

  // layout of the test level "exit_room":
  //
  // W W W W W
  // W F F F W
  // W F E F W
  // W F F F W
  // W W W W W

  /** WTF? . */
  @BeforeEach
  public void setup() {
    TestLevels.load("exit_room");
  }

  /** WTF? . */
  @AfterEach
  public void cleanup() {
    TestLevels.unload();
  }

  /** WTF? . */
  @Test
  public void tilesInRangeCenterNotInLevel() {
    var tiles = LevelUtils.tilesInRange(new Point(-10, -10), 1.1f);
    assertEquals(0, tiles.size());
  }

  /** WTF? . */
  @Test
  public void tilesInRangeOnlyCorners() {
    var tiles = LevelUtils.tilesInRange(new Point(0, 0), 1.1f);
    assertEquals(3, tiles.size());
    assertTrue(
        tiles.stream().anyMatch(tile -> tile.coordinate().x() == 0 && tile.coordinate().y() == 0));
    assertTrue(
        tiles.stream().anyMatch(tile -> tile.coordinate().x() == 1 && tile.coordinate().y() == 0));
    assertTrue(
        tiles.stream().anyMatch(tile -> tile.coordinate().x() == 0 && tile.coordinate().y() == 1));
  }

  /** WTF? . */
  @Test
  public void tilesInRangeNotInCorner() {
    var tiles = LevelUtils.tilesInRange(new Point(0.5f, 0.5f), 0.6f);
    assertEquals(3, tiles.size());
    assertTrue(
        tiles.stream().anyMatch(tile -> tile.coordinate().x() == 0 && tile.coordinate().y() == 0));
    assertTrue(
        tiles.stream().anyMatch(tile -> tile.coordinate().x() == 1 && tile.coordinate().y() == 0));
    assertTrue(
        tiles.stream().anyMatch(tile -> tile.coordinate().x() == 0 && tile.coordinate().y() == 1));
  }
}
