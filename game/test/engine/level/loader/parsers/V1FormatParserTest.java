package engine.level.loader.parsers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import engine.level.DungeonLevel;
import engine.level.utils.Coordinate;
import engine.level.utils.DesignLabel;
import engine.level.utils.LevelElement;
import engine.utils.Point;
import java.io.BufferedReader;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

/** Tests for {@link V1FormatParser} class. */
public class V1FormatParserTest {

  // default layout:
  //
  // y=0  W F W W
  // y=1  W F E W
  // y=2  W W W W

  private static final String LEVEL_NAME = "v1formatparsertest";

  private static final String LEVEL =
      """
      FIRE
      1,1
      1,1;2,1
      WFWW
      WFEW
      WWWW
      """;

  private final V1FormatParser parser = new V1FormatParser();

  private DungeonLevel parse(String levelData) throws Exception {
    return parser.parseLevel(new BufferedReader(new StringReader(levelData)), LEVEL_NAME);
  }

  private static LevelElement element(DungeonLevel level, int x, int y) {
    return level.layout()[y][x].levelElement();
  }

  @Test
  void parseLevelHappyPath() throws Exception {
    DungeonLevel level = parse(LEVEL);

    assertSame(DungeonLevel.class, level.getClass());
    assertEquals(DesignLabel.FIRE, level.baseDesignLabel());
    assertEquals(3, level.layout().length);
    assertEquals(4, level.layout()[0].length);
    assertEquals(LevelElement.WALL, element(level, 0, 0));
    assertEquals(LevelElement.FLOOR, element(level, 1, 0));
    assertEquals(LevelElement.EXIT, element(level, 2, 1));
    assertEquals(LevelElement.WALL, element(level, 1, 2));
    assertEquals(1, level.startTiles().size());
    assertEquals(new Coordinate(1, 1), level.startTiles().getFirst().coordinate());
    assertEquals(new Point(1, 1), level.startTiles().getFirst().position());
    assertEquals(Map.of("Point0", new Point(1, 1), "Point1", new Point(2, 1)), level.namedPoints());
    assertEquals(new Point(2, 1), level.getPoint(1));
    assertTrue(level.decorations().isEmpty());
  }

  @Test
  void parseLevelWithoutCustomPointsHasNoNamedPoints() throws Exception {
    DungeonLevel level = parse(LEVEL.replace("1,1;2,1\n", "\n"));

    assertTrue(level.namedPoints().isEmpty());
    assertEquals(List.of(level.layout()[1][1]), level.startTiles());
  }

  @Test
  void parseLevelPlayerPositionWithoutTileThrowsRuntimeException() {
    RuntimeException e =
        assertThrows(RuntimeException.class, () -> parse(LEVEL.replace("\n1,1\n", "\n9,9\n")));

    assertSame(RuntimeException.class, e.getClass());
  }

  @Test
  void parseLevelInvalidDesignLabelThrowsRuntimeException() {
    assertThrows(RuntimeException.class, () -> parse(LEVEL.replace("FIRE", "LAVA")));
  }

  @Test
  void parseLevelInvalidPlayerPositionThrowsRuntimeException() {
    assertThrows(RuntimeException.class, () -> parse(LEVEL.replace("\n1,1\n", "\na,b\n")));
  }

  @Test
  void parseLevelPlayerPositionWithWrongPartCountThrowsRuntimeException() {
    assertThrows(RuntimeException.class, () -> parse(LEVEL.replace("\n1,1\n", "\n1\n")));
  }

  @Test
  void parseLevelInvalidCustomPointThrowsRuntimeException() {
    assertThrows(RuntimeException.class, () -> parse(LEVEL.replace("1,1;2,1", "1,1;x,1")));
  }

  @Test
  void parseLevelCustomPointWithWrongPartCountThrowsRuntimeException() {
    assertThrows(RuntimeException.class, () -> parse(LEVEL.replace("1,1;2,1", "1,1;2")));
  }

  @Test
  void parseLevelInvalidLayoutCharacterThrowsIllegalArgumentException() {
    assertThrows(IllegalArgumentException.class, () -> parse(LEVEL.replace("WFEW", "WFXW")));
  }

  @Test
  void parseLevelShorterLayoutRowThrowsStringIndexOutOfBoundsException() {
    assertThrows(StringIndexOutOfBoundsException.class, () -> parse(LEVEL.replace("WFEW", "WFE")));
  }

  @Test
  void parseLevelWithoutLayoutThrowsNoSuchElementException() {
    assertThrows(NoSuchElementException.class, () -> parse("FIRE\n1,1\n\n"));
  }

  @Test
  void serializeLevelThrowsUnsupportedOperationException() throws Exception {
    DungeonLevel level = parse(LEVEL);
    assertThrows(UnsupportedOperationException.class, () -> parser.serializeLevel(level));
  }

  @Test
  void serializeLevelNullThrowsUnsupportedOperationException() {
    assertThrows(UnsupportedOperationException.class, () -> parser.serializeLevel(null));
  }
}
