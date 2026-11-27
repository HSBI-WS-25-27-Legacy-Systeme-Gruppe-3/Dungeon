package engine.level.elements.astar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import engine.level.Tile;
import engine.level.elements.tile.FloorTile;
import engine.level.utils.Coordinate;
import engine.level.utils.DesignLabel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for {@link TileHeuristic} class. */
public class TileHeuristicTest {

  // from = (1, 2), to = (4, 6)
  // (manhattan distance = 3 + 4 = 7)
  private TileHeuristic heuristic;
  private Tile start;
  private Tile goal;

  @BeforeEach
  void setup() {
    heuristic = new TileHeuristic();
    start = new FloorTile(new Coordinate(1, 2), DesignLabel.DEFAULT);
    goal = new FloorTile(new Coordinate(4, 6), DesignLabel.DEFAULT);
  }

  @Test
  void estimateDistance() {
    assertEquals(7f, heuristic.estimate(start, goal), 0.0f);
  }

  @Test
  void estimateStartNull() {
    assertThrows(NullPointerException.class, () -> heuristic.estimate(null, goal));
  }

  @Test
  void estimateGoalNull() {
    assertThrows(NullPointerException.class, () -> heuristic.estimate(start, null));
  }

  @Test
  void estimateIdenticalTile() {
    assertEquals(0f, heuristic.estimate(start, start), 0.0f);
  }
}
