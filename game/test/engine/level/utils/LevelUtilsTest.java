package engine.level.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.ai.pfa.DefaultGraphPath;
import com.badlogic.gdx.ai.pfa.GraphPath;
import engine.Entity;
import engine.Game;
import engine.components.PlayerComponent;
import engine.components.PositionComponent;
import engine.level.DungeonLevel;
import engine.level.Tile;
import engine.level.elements.ILevel;
import engine.resources.Resources;
import engine.systems.LevelSystem;
import engine.utils.Point;
import engine.utils.components.MissingComponentException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for {@link LevelUtils} class. */
public class LevelUtilsTest {

  // default layout:
  //
  // W W W W W W W
  // W F F F W F W
  // W F E F W H W
  // W F F F W D W
  // W W W W W W W

  private DungeonLevel level;

  @BeforeEach
  void setup() {
    Game.add(new LevelSystem());
    level = level("WWWWWWW", "WFFFWFW", "WFEFWHW", "WFFFWDW", "WWWWWWW");
    Game.currentLevel(level);
  }

  @AfterEach
  void cleanup() {
    Game.removeAllEntities();
    Game.removeAllSystems();
    Resources.remove(ILevel.class);
  }

  private static DungeonLevel level(String... rows) {
    LevelElement[][] layout = new LevelElement[rows.length][];

    for (int y = 0; y < rows.length; y++) {
      layout[y] = new LevelElement[rows[y].length()];

      for (int x = 0; x < rows[y].length(); x++) {
        layout[y][x] =
            switch (rows[y].charAt(x)) {
              case 'F' -> LevelElement.FLOOR;
              case 'E' -> LevelElement.EXIT;
              case 'D' -> LevelElement.DOOR;
              case 'H' -> LevelElement.HOLE;
              default -> LevelElement.WALL;
            };
      }
    }

    return new DungeonLevel(layout, DesignLabel.DEFAULT);
  }

  private static Entity entityAt(float x, float y) {
    Entity entity = new Entity();
    entity.add(new PositionComponent(x, y));

    return entity;
  }

  private static Entity addPlayerAt(float x, float y) {
    Entity player = entityAt(x, y);
    player.add(new PlayerComponent());
    Game.add(player);

    return player;
  }

  private Tile tile(int x, int y) {
    return level.layout()[y][x];
  }

  private static List<Coordinate> coordinates(GraphPath<Tile> path) {
    List<Coordinate> coordinates = new ArrayList<>();
    path.forEach(tile -> coordinates.add(tile.coordinate()));

    return coordinates;
  }

  private static Set<Coordinate> coordinates(List<Tile> tiles) {
    return tiles.stream().map(Tile::coordinate).collect(Collectors.toSet());
  }

  @Test
  void calculatePathCoordinatesHappyPath() {
    GraphPath<Tile> path = LevelUtils.calculatePath(new Coordinate(1, 1), new Coordinate(3, 3));

    assertTrue(path.getCount() > 0);
    assertEquals(new Coordinate(1, 1), path.get(0).coordinate());
    assertEquals(new Coordinate(3, 3), LevelUtils.lastTile(path).coordinate());
  }

  @Test
  void calculatePathCoordinatesWithoutTile() {
    GraphPath<Tile> path = LevelUtils.calculatePath(new Coordinate(-1, -1), new Coordinate(3, 3));

    assertTrue(path instanceof DefaultGraphPath);
    assertEquals(0, path.getCount());
  }

  @Test
  void calculatePathCoordinatesNotAccessible() {
    GraphPath<Tile> path = LevelUtils.calculatePath(new Coordinate(0, 0), new Coordinate(3, 3));

    assertTrue(path instanceof DefaultGraphPath);
    assertEquals(0, path.getCount());
  }

  @Test
  void calculatePathCoordinatesNoPathAvailable() {
    GraphPath<Tile> path = LevelUtils.calculatePath(new Coordinate(1, 1), new Coordinate(5, 1));

    assertTrue(path instanceof DefaultGraphPath);
    assertEquals(0, path.getCount());
  }

  @Test
  void calculatePathCoordinatesSameCoordinate() {
    GraphPath<Tile> path = LevelUtils.calculatePath(new Coordinate(1, 1), new Coordinate(1, 1));
    assertEquals(List.of(new Coordinate(1, 1)), coordinates(path));
  }

  @Test
  void calculatePathInsideWallLShape() {
    GraphPath<Tile> path =
        LevelUtils.calculatePathInsideWall(new Coordinate(1, 1), new Coordinate(3, 3));

    assertEquals(
        List.of(
            new Coordinate(1, 1),
            new Coordinate(2, 1),
            new Coordinate(3, 1),
            new Coordinate(3, 2),
            new Coordinate(3, 3)),
        coordinates(path));
  }

  @Test
  void calculatePathInsideWallNotAccessibleTile() {
    GraphPath<Tile> path =
        LevelUtils.calculatePathInsideWall(new Coordinate(0, 1), new Coordinate(2, 1));

    assertEquals(
        List.of(new Coordinate(0, 1), new Coordinate(1, 1), new Coordinate(2, 1)),
        coordinates(path));
  }

  @Test
  void calculatePathInsideWallWithGap() {
    level.layout()[1][2] = null;

    GraphPath<Tile> path =
        LevelUtils.calculatePathInsideWall(new Coordinate(1, 1), new Coordinate(3, 1));

    assertEquals(List.of(new Coordinate(1, 1), new Coordinate(3, 1)), coordinates(path));
  }

  @Test
  void calculatePathInsideWallSameCoordinate() {
    GraphPath<Tile> path =
        LevelUtils.calculatePathInsideWall(new Coordinate(1, 1), new Coordinate(1, 1));

    assertEquals(List.of(new Coordinate(1, 1)), coordinates(path));
  }

  @Test
  void calculatePathToRandomTileInRangePointHappyPath() {
    Point center = new Point(2.5f, 2.5f);
    Set<Coordinate> reachable = coordinates(LevelUtils.accessibleTilesInRange(center, 1));

    GraphPath<Tile> path = LevelUtils.calculatePathToRandomTileInRange(center, 1);

    assertEquals(new Coordinate(2, 2), path.get(0).coordinate());
    assertTrue(reachable.contains(LevelUtils.lastTile(path).coordinate()));
  }

  @Test
  void calculatePathToRandomTileInRangePointNoAccessibleTile() {
    Point center = new Point(0.5f, 0.5f);
    GraphPath<Tile> path = LevelUtils.calculatePathToRandomTileInRange(center, 0);

    assertEquals(coordinates(LevelUtils.calculatePath(center, center)), coordinates(path));
    assertEquals(0, path.getCount());
  }

  @Test
  void calculatePathToRandomTileInRangeEntityHappyPath() {
    Entity entity = entityAt(2.5f, 2.5f);
    Set<Coordinate> reachable =
        coordinates(LevelUtils.accessibleTilesInRange(new Point(2.5f, 2.5f), 1));

    GraphPath<Tile> path = LevelUtils.calculatePathToRandomTileInRange(entity, 1);

    assertEquals(new Coordinate(2, 2), path.get(0).coordinate());
    assertTrue(reachable.contains(LevelUtils.lastTile(path).coordinate()));
  }

  @Test
  void calculatePathToRandomTileInRangeEntityNullThrowsNullPointerException() {
    assertThrows(
        NullPointerException.class,
        () -> LevelUtils.calculatePathToRandomTileInRange((Entity) null, 1));
  }

  @Test
  void calculatePathToRandomTileInRangeEntityWithoutPositionThrowsMissingComponentException() {
    assertThrows(
        MissingComponentException.class,
        () -> LevelUtils.calculatePathToRandomTileInRange(new Entity(), 1));
  }

  @Test
  void calculatePathEntitiesHappyPath() {
    GraphPath<Tile> path = LevelUtils.calculatePath(entityAt(1.5f, 1.5f), entityAt(3.5f, 3.5f));

    assertEquals(new Coordinate(1, 1), path.get(0).coordinate());
    assertEquals(new Coordinate(3, 3), LevelUtils.lastTile(path).coordinate());
  }

  @Test
  void calculatePathEntitiesWithoutPositionThrowsMissingComponentException() {
    Entity from = entityAt(1.5f, 1.5f);

    assertThrows(
        MissingComponentException.class, () -> LevelUtils.calculatePath(from, new Entity()));
  }

  @Test
  void calculatePathEntitiesNullThrowsNullPointerException() {
    Entity from = entityAt(1.5f, 1.5f);
    assertThrows(NullPointerException.class, () -> LevelUtils.calculatePath(from, (Entity) null));
  }

  @Test
  void calculatePathEntitiesSameEntity() {
    Entity entity = entityAt(1.5f, 1.5f);

    GraphPath<Tile> path = LevelUtils.calculatePath(entity, entity);

    assertEquals(
        coordinates(LevelUtils.calculatePath(new Coordinate(1, 1), new Coordinate(1, 1))),
        coordinates(path));
  }

  @Test
  void calculatePathToPlayerHappyPath() {
    addPlayerAt(3.5f, 3.5f);
    GraphPath<Tile> path = LevelUtils.calculatePathToPlayer(entityAt(1.5f, 1.5f));

    assertEquals(new Coordinate(1, 1), path.get(0).coordinate());
    assertEquals(new Coordinate(3, 3), LevelUtils.lastTile(path).coordinate());
  }

  @Test
  void calculatePathToPlayerWithoutPlayer() {
    Entity entity = entityAt(1.5f, 1.5f);
    GraphPath<Tile> path = LevelUtils.calculatePathToPlayer(entity);

    assertEquals(coordinates(LevelUtils.calculatePath(entity, entity)), coordinates(path));
  }

  @Test
  void lastTileHappyPath() {
    GraphPath<Tile> path = new DefaultGraphPath<>();
    path.add(tile(1, 1));
    path.add(tile(2, 1));
    path.add(tile(3, 1));

    assertSame(tile(3, 1), LevelUtils.lastTile(path));
  }

  @Test
  void lastTileEmptyPathThrowsIndexOutOfBoundsException() {
    GraphPath<Tile> path = new DefaultGraphPath<>();
    assertThrows(IndexOutOfBoundsException.class, () -> LevelUtils.lastTile(path));
  }

  @Test
  void lastTileNullPathThrowsNullPointerException() {
    assertThrows(NullPointerException.class, () -> LevelUtils.lastTile(null));
  }

  @Test
  void tilesInRangeHappyPath() {
    level.layout()[1][1] = null;

    List<Tile> tiles = LevelUtils.tilesInRange(new Point(2.5f, 2.5f), 1);

    assertFalse(tiles.contains(null));
    assertEquals(
        Set.of(
            new Coordinate(2, 1),
            new Coordinate(3, 1),
            new Coordinate(1, 2),
            new Coordinate(2, 2),
            new Coordinate(3, 2),
            new Coordinate(1, 3),
            new Coordinate(2, 3),
            new Coordinate(3, 3)),
        coordinates(tiles));
  }

  @ParameterizedTest
  @MethodSource("tileCenters")
  void tilesInRangeZeroRadius(Point center, Coordinate expected) {
    List<Tile> tiles = LevelUtils.tilesInRange(center, 0);
    assertEquals(Set.of(expected), coordinates(tiles));
  }

  private static Stream<Arguments> tileCenters() {
    return Stream.of(
        Arguments.of(new Point(2.5f, 2.5f), new Coordinate(2, 2)),
        Arguments.of(new Point(0.5f, 0.5f), new Coordinate(0, 0)),
        Arguments.of(new Point(5.5f, 3.5f), new Coordinate(5, 3)));
  }

  @Test
  void tilesInRangeCenterNotInLevel() {
    List<Tile> tiles = LevelUtils.tilesInRange(new Point(-10, -10), 1.1f);
    assertEquals(0, tiles.size());
  }

  @Test
  void tilesInRangeOnlyCorners() {
    List<Tile> tiles = LevelUtils.tilesInRange(new Point(0, 0), 1.1f);

    assertEquals(3, tiles.size());
    assertTrue(
        tiles.stream().anyMatch(tile -> tile.coordinate().x() == 0 && tile.coordinate().y() == 0));
    assertTrue(
        tiles.stream().anyMatch(tile -> tile.coordinate().x() == 1 && tile.coordinate().y() == 0));
    assertTrue(
        tiles.stream().anyMatch(tile -> tile.coordinate().x() == 0 && tile.coordinate().y() == 1));
  }

  @Test
  void tilesInRangeNotInCorner() {
    List<Tile> tiles = LevelUtils.tilesInRange(new Point(0.5f, 0.5f), 0.6f);

    assertEquals(3, tiles.size());
    assertTrue(
        tiles.stream().anyMatch(tile -> tile.coordinate().x() == 0 && tile.coordinate().y() == 0));
    assertTrue(
        tiles.stream().anyMatch(tile -> tile.coordinate().x() == 1 && tile.coordinate().y() == 0));
    assertTrue(
        tiles.stream().anyMatch(tile -> tile.coordinate().x() == 0 && tile.coordinate().y() == 1));
  }

  @ParameterizedTest
  @MethodSource("negativeRadii")
  void tilesInRangeNegativeRadius(float radius, Set<Coordinate> expected) {
    List<Tile> tiles = LevelUtils.tilesInRange(new Point(2.5f, 2.5f), radius);
    assertEquals(expected, coordinates(tiles));
  }

  private static Stream<Arguments> negativeRadii() {
    Set<Coordinate> cross =
        Set.of(
            new Coordinate(2, 1),
            new Coordinate(1, 2),
            new Coordinate(2, 2),
            new Coordinate(3, 2),
            new Coordinate(2, 3));

    return Stream.of(
        Arguments.of(-0.6f, cross),
        Arguments.of(-1f, cross),
        Arguments.of(-1.5f, Set.of(new Coordinate(2, 2))));
  }

  @Test
  void accessibleTilesInRangeHappyPath() {
    List<Tile> tiles = LevelUtils.accessibleTilesInRange(new Point(3.5f, 2.5f), 1);

    assertEquals(
        Set.of(
            new Coordinate(2, 1),
            new Coordinate(3, 1),
            new Coordinate(2, 2),
            new Coordinate(3, 2),
            new Coordinate(2, 3),
            new Coordinate(3, 3)),
        coordinates(tiles));
  }

  @Test
  void randomAccessibleTileInRangeAsPointHappyPath() {
    Optional<Point> point = LevelUtils.randomAccessibleTileInRangeAsPoint(new Point(1.5f, 1.5f), 0);
    assertEquals(Optional.of(tile(1, 1).position()), point);
  }

  @Test
  void randomAccessibleTileInRangeAsPointNoAccessibleTile() {
    Optional<Point> point = LevelUtils.randomAccessibleTileInRangeAsPoint(new Point(0.5f, 0.5f), 0);
    assertTrue(point.isEmpty());
  }

  @ParameterizedTest
  @MethodSource("entityRanges")
  void entityInRangeHappyPath(float range, boolean expected) {
    Entity first = entityAt(1, 1);
    Entity second = entityAt(3, 1);

    assertEquals(expected, LevelUtils.entityInRange(first, second, range));
  }

  private static Stream<Arguments> entityRanges() {
    return Stream.of(Arguments.of(2f, true), Arguments.of(3f, true), Arguments.of(1.9f, false));
  }

  @Test
  void entityInRangeWithoutPositionThrowsMissingComponentException() {
    Entity first = entityAt(1, 1);
    assertThrows(
        MissingComponentException.class, () -> LevelUtils.entityInRange(first, new Entity(), 1));
  }

  @Test
  void entityInRangeNullEntityThrowsNullPointerException() {
    Entity first = entityAt(1, 1);
    assertThrows(NullPointerException.class, () -> LevelUtils.entityInRange(first, null, 1));
  }

  @ParameterizedTest
  @MethodSource("borderRanges")
  void entityInRangeBorderRanges(float range, boolean expected) {
    Entity first = entityAt(1, 1);
    Entity second = entityAt(1, 1);

    assertEquals(expected, LevelUtils.entityInRange(first, second, range));
  }

  private static Stream<Arguments> borderRanges() {
    return Stream.of(Arguments.of(0f, true), Arguments.of(-0f, true), Arguments.of(-1f, false));
  }

  @Test
  void playerInRangeHappyPath() {
    addPlayerAt(1, 1);
    assertTrue(LevelUtils.playerInRange(entityAt(3, 1), 2));
  }

  @Test
  void freeTileHappyPath() {
    Optional<Tile> tile = LevelUtils.freeTile();

    assertTrue(tile.isPresent());
    assertTrue(LevelUtils.isFreeTile(tile.get()));
  }

  @Test
  void freeTileNoFreeTile() {
    Game.currentLevel(level("WWW", "WWW", "WWW"));
    assertTrue(LevelUtils.freeTile().isEmpty());
  }

  @Test
  void freeTileWithoutLevel() {
    Resources.remove(ILevel.class);
    assertTrue(LevelUtils.freeTile().isEmpty());
  }

  @Test
  void neighboursHappyPath() {
    Set<Tile> neighbours = LevelUtils.neighbours(tile(2, 2));
    assertEquals(Set.of(tile(1, 2), tile(3, 2), tile(2, 1), tile(2, 3)), neighbours);
  }

  @Test
  void neighboursAtLevelBorder() {
    Set<Tile> neighbours = LevelUtils.neighbours(tile(0, 0));
    assertEquals(Set.of(tile(1, 0), tile(0, 1)), neighbours);
  }

  @Test
  void neighboursWithoutLevelThrowsNullPointerException() {
    Tile tile = tile(2, 2);
    Resources.remove(ILevel.class);

    assertThrows(NullPointerException.class, () -> LevelUtils.neighbours(tile));
  }

  @Test
  void isFreeTileHappyPath() {
    assertTrue(LevelUtils.isFreeTile(tile(1, 1)));
  }

  @Test
  void isFreeTileOccupied() {
    Entity entity = entityAt(1.5f, 1.5f);
    Game.add(entity);

    assertFalse(LevelUtils.isFreeTile(tile(1, 1)));
  }

  @Test
  void isFreeTileNullThrowsNullPointerException() {
    assertThrows(NullPointerException.class, () -> LevelUtils.isFreeTile(null));
  }

  @Test
  void changeVisibilityForAreaHappyPath() {
    LevelUtils.changeVisibilityForArea(new Coordinate(1, 3), new Coordinate(3, 1), false);

    for (Tile[] row : level.layout()) {
      for (Tile tile : row) {
        boolean inside =
            LevelUtils.isTileWithinArea(tile, new Coordinate(1, 3), new Coordinate(3, 1));

        assertEquals(!inside, tile.visible());
      }
    }
  }

  @Test
  void changeVisibilityForAreaTopLeftRightOfBottomRight() {
    LevelUtils.changeVisibilityForArea(new Coordinate(3, 3), new Coordinate(1, 1), false);

    for (Tile[] row : level.layout()) {
      for (Tile tile : row) {
        assertTrue(tile.visible());
      }
    }
  }

  @ParameterizedTest
  @MethodSource("areaTiles")
  void isTileWithinAreaHappyPath(int x, int y, boolean expected) {
    boolean inside =
        LevelUtils.isTileWithinArea(tile(x, y), new Coordinate(1, 3), new Coordinate(3, 1));

    assertEquals(expected, inside);
  }

  private static Stream<Arguments> areaTiles() {
    return Stream.of(
        Arguments.of(2, 2, true),
        Arguments.of(1, 3, true),
        Arguments.of(3, 1, true),
        Arguments.of(0, 2, false),
        Arguments.of(2, 4, false));
  }

  @Test
  void isTileWithinAreaTileNullThrowsNullPointerException() {
    assertThrows(
        NullPointerException.class,
        () -> LevelUtils.isTileWithinArea(null, new Coordinate(1, 3), new Coordinate(3, 1)));
  }

  @Test
  void isTileWithinAreaCoordinateNullThrowsNullPointerException() {
    Tile tile = tile(2, 2);

    assertThrows(
        NullPointerException.class,
        () -> LevelUtils.isTileWithinArea(tile, null, new Coordinate(3, 1)));
  }

  @ParameterizedTest
  @MethodSource("playerPositions")
  void isPlayerInAreaHappyPath(float x, float y, boolean expected) {
    addPlayerAt(x, y);
    assertEquals(expected, LevelUtils.isPlayerInArea(new Coordinate(1, 3), new Coordinate(3, 1)));
  }

  private static Stream<Arguments> playerPositions() {
    return Stream.of(Arguments.of(2.5f, 2.5f, true), Arguments.of(5.5f, 1.5f, false));
  }

  @Test
  void isPlayerInAreaWithoutPlayer() {
    assertFalse(LevelUtils.isPlayerInArea(new Coordinate(1, 3), new Coordinate(3, 1)));
  }

  @Test
  void isPlayerInAreaPlayerWithoutPositionThrowsMissingComponentException() {
    Entity player = new Entity();
    player.add(new PlayerComponent());
    Game.add(player);

    assertThrows(
        MissingComponentException.class,
        () -> LevelUtils.isPlayerInArea(new Coordinate(1, 3), new Coordinate(3, 1)));
  }

  @Test
  void tilesInAreaHappyPath() {
    List<Tile> tiles = LevelUtils.tilesInArea(new Coordinate(1, 3), new Coordinate(3, 1));

    Set<Coordinate> expected = new HashSet<>();
    for (int x = 1; x <= 3; x++) {
      for (int y = 1; y <= 3; y++) {
        expected.add(new Coordinate(x, y));
      }
    }

    assertEquals(9, tiles.size());
    assertEquals(expected, coordinates(tiles));
  }

  @Test
  void tilesInAreaTopLeftRightOfBottomRight() {
    List<Tile> tiles = LevelUtils.tilesInArea(new Coordinate(3, 3), new Coordinate(1, 1));
    assertTrue(tiles.isEmpty());
  }

  @Test
  void tilesInAreaOutOfBoundsThrowsArrayIndexOutOfBoundsException() {
    assertThrows(
        ArrayIndexOutOfBoundsException.class,
        () -> LevelUtils.tilesInArea(new Coordinate(5, 6), new Coordinate(8, 1)));
  }

  @Test
  void tilesInAreaWithoutLevelThrowsNullPointerException() {
    Resources.remove(ILevel.class);

    assertThrows(
        NullPointerException.class,
        () -> LevelUtils.tilesInArea(new Coordinate(1, 3), new Coordinate(3, 1)));
  }

  @Test
  void tintAreaHappyPath() {
    LevelUtils.tintArea(new Coordinate(1, 3), new Coordinate(3, 1), 0xFF0000FF);

    for (Tile[] row : level.layout()) {
      for (Tile tile : row) {
        boolean inside =
            LevelUtils.isTileWithinArea(tile, new Coordinate(1, 3), new Coordinate(3, 1));
        assertEquals(inside ? 0xFF0000FF : -1, tile.tintColor());
      }
    }
  }

  @Test
  void tintAreaSameStartAndEnd() {
    LevelUtils.tintArea(new Coordinate(2, 2), new Coordinate(2, 2), 0xFF0000FF);

    for (Tile[] row : level.layout()) {
      for (Tile tile : row) {
        assertEquals(tile == tile(2, 2) ? 0xFF0000FF : -1, tile.tintColor());
      }
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, -2, Integer.MIN_VALUE})
  void tintAreaSpecialColors(int color) {
    tile(2, 2).tintColor(0xFF0000FF);
    LevelUtils.tintArea(new Coordinate(2, 2), new Coordinate(2, 2), color);

    assertEquals(color, tile(2, 2).tintColor());
  }
}
