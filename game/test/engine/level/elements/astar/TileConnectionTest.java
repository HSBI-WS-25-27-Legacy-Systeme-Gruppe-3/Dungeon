package engine.level.elements.astar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import engine.level.Tile;
import engine.level.elements.tile.FloorTile;
import engine.level.utils.Coordinate;
import engine.level.utils.DesignLabel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for {@link TileConnection} class. */
public class TileConnectionTest {

  // from = (1, 2), to = (4, 6)
  // (manhattan distance = 3 + 4 = 7)
  private Tile from;
  private Tile to;

  @BeforeEach
  void setup() {
    from = new FloorTile(new Coordinate(1, 2), DesignLabel.DEFAULT);
    to = new FloorTile(new Coordinate(4, 6), DesignLabel.DEFAULT);
  }

  @Test
  void constructorCostIsDistance() {
    TileConnection connection = new TileConnection(from, to);

    assertEquals(7f, connection.getCost(), 0.0f);
    assertSame(from, connection.getFromNode());
    assertSame(to, connection.getToNode());
  }

  @Test
  void constructorFromNull() {
    assertThrows(NullPointerException.class, () -> new TileConnection(null, to));
  }

  @Test
  void constructorToNull() {
    assertThrows(NullPointerException.class, () -> new TileConnection(from, null));
  }

  @Test
  void constructorIdenticalTile() {
    TileConnection connection = new TileConnection(from, from);

    assertEquals(0f, connection.getCost(), 0.0f);
    assertSame(from, connection.getFromNode());
    assertSame(from, connection.getToNode());
  }

  @Test
  void constructorTileWithoutCoordinate() {
    Tile noCoordinate = new FloorTile(null, DesignLabel.DEFAULT);

    assertThrows(NullPointerException.class, () -> new TileConnection(noCoordinate, to));
    assertThrows(NullPointerException.class, () -> new TileConnection(from, noCoordinate));
  }

  @Test
  void equalsSameInstance() {
    TileConnection connection = new TileConnection(from, to);
    assertTrue(connection.equals(connection));
  }

  @Test
  void equalsOtherInstanceSameTiles() {
    TileConnection connection = new TileConnection(from, to);
    assertTrue(connection.equals(new TileConnection(from, to)));
  }

  @Test
  void equalsNull() {
    TileConnection connection = new TileConnection(from, to);
    assertThrows(NullPointerException.class, () -> connection.equals(null));
  }

  @Test
  void equalsOtherClass() {
    TileConnection connection = new TileConnection(from, to);
    assertFalse(connection.equals(from));
  }

  @Test
  void equalsDifferentTileInstancesSameCoordinates() {
    TileConnection connection = new TileConnection(from, to);
    Tile fromCopy = new FloorTile(new Coordinate(1, 2), DesignLabel.DEFAULT);
    Tile toCopy = new FloorTile(new Coordinate(4, 6), DesignLabel.DEFAULT);

    assertFalse(connection.equals(new TileConnection(fromCopy, toCopy)));
  }
}
