package engine.level.loader.parsers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import engine.level.DungeonLevel;
import engine.level.Tile;
import engine.level.utils.Coordinate;
import engine.level.utils.DesignLabel;
import engine.level.utils.LevelElement;
import engine.utils.Point;
import engine.utils.Tuple;
import feature.entities.deco.Deco;
import java.io.BufferedReader;
import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tests for {@link V2FormatParser} class. */
public class V2FormatParserTest {

  // default layout:
  //
  // y=2  W F W W
  // y=1  W F E W
  // y=0  W W W W

  private static final String LEVEL_NAME = "v2formatparsertest";

  private static final String LEVEL =
      """
      FOREST
      1,1;2,1
      spawn:2.5,1.5;boss:1,2
      SoulweaverWorkbench:1.5,1.5
      WFWW
      WFEW
      WWWW
      """;

  private final V2FormatParser parser = new V2FormatParser();

  private DungeonLevel parse(String levelData) throws Exception {
    return parser.parseLevel(new BufferedReader(new StringReader(levelData)), LEVEL_NAME);
  }

  private static LevelElement element(DungeonLevel level, int x, int y) {
    return level.layout()[y][x].levelElement();
  }

  private static DungeonLevel level(String... rows) {
    LevelElement[][] layout = new LevelElement[rows.length][];

    for (int y = 0; y < rows.length; y++) {
      layout[y] = new LevelElement[rows[y].length()];

      for (int x = 0; x < rows[y].length(); x++) {
        layout[y][x] = LevelFormatParser.getLevelElementFromChar(rows[y].charAt(x));
      }
    }

    return new DungeonLevel(layout, DesignLabel.DEFAULT);
  }

  @Test
  void serializeLevelLayoutHappyPath() {
    DungeonLevel level = level("WWWW", "WFEW", "SHFS", "SSSS");
    String serialized = V2FormatParser.serializeLevelLayout(level.layout());

    assertEquals(String.join(System.lineSeparator(), "SHFS", "WFEW", "WWWW"), serialized);
  }

  @Test
  void serializeLevelLayoutEmptyArrayReturnsEmptyString() {
    assertEquals("", V2FormatParser.serializeLevelLayout(new Tile[0][0]));
  }

  @Test
  void serializeLevelLayoutNullThrowsNullPointerException() {
    assertThrows(NullPointerException.class, () -> V2FormatParser.serializeLevelLayout(null));
  }

  @Test
  void loadLevelLayoutHappyPath() {
    LevelElement[][] layout = V2FormatParser.loadLevelLayout(List.of("WFD", "HES", "WWW"));

    assertEquals(3, layout.length);
    assertEquals(3, layout[0].length);
    assertEquals(
        List.of(LevelElement.WALL, LevelElement.FLOOR, LevelElement.DOOR), List.of(layout[2]));
    assertEquals(
        List.of(LevelElement.HOLE, LevelElement.EXIT, LevelElement.SKIP), List.of(layout[1]));
    assertEquals(
        List.of(LevelElement.WALL, LevelElement.WALL, LevelElement.WALL), List.of(layout[0]));
  }

  @Test
  void loadLevelLayoutShorterLineThrowsStringIndexOutOfBoundsException() {
    assertThrows(
        StringIndexOutOfBoundsException.class,
        () -> V2FormatParser.loadLevelLayout(List.of("WFW", "WF", "WWW")));
  }

  @Test
  void serializeStartingPositionsHappyPath() {
    DungeonLevel level = level("WWWW", "WFFW", "WWWW");
    String serialized =
        V2FormatParser.serializeStartingPositions(
            List.of(level.layout()[1][1], level.layout()[1][2]));

    assertEquals("1.0,1.0;2.0,1.0", serialized);
  }

  @Test
  void serializeNamedPointsHappyPath() {
    Map<String, Point> namedPoints = new LinkedHashMap<>();
    namedPoints.put("spawn", new Point(2.5f, 1.5f));
    namedPoints.put("boss", new Point(1, 2));

    assertEquals("spawn:2.5,1.5;boss:1.0,2.0", V2FormatParser.serializeNamedPoints(namedPoints));
  }

  @Test
  void serializeDecorationListHappyPath() {
    List<Tuple<Deco, Point>> decorations =
        List.of(
            new Tuple<>(Deco.SoulweaverWorkbench, new Point(1.5f, 1.5f)),
            new Tuple<>(Deco.SoulweaverWorkbench, new Point(3, 4)));

    assertEquals(
        "SoulweaverWorkbench:1.5,1.5;SoulweaverWorkbench:3.0,4.0",
        V2FormatParser.serializeDecorationList(decorations));
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

  @Test
  void parseLevelHappyPath() throws Exception {
    DungeonLevel level = parse(LEVEL);

    assertSame(DungeonLevel.class, level.getClass());
    assertEquals(DesignLabel.FOREST, level.baseDesignLabel());
    assertEquals(3, level.layout().length);
    assertEquals(4, level.layout()[0].length);
    assertEquals(LevelElement.FLOOR, element(level, 1, 2));
    assertEquals(LevelElement.EXIT, element(level, 2, 1));
    assertEquals(LevelElement.WALL, element(level, 1, 0));
    assertEquals(2, level.startTiles().size());
    assertEquals(new Coordinate(1, 1), level.startTiles().get(0).coordinate());
    assertEquals(new Coordinate(2, 1), level.startTiles().get(1).coordinate());
    assertEquals(
        Map.of("spawn", new Point(2.5f, 1.5f), "boss", new Point(1, 2)), level.namedPoints());
    assertEquals(1, level.decorations().size());
    assertEquals(Deco.SoulweaverWorkbench, level.decorations().getFirst().a());
    assertEquals(new Point(1.5f, 1.5f), level.decorations().getFirst().b());
  }

  @Test
  void parseLevelStartTileWithoutTileIsIgnored() throws Exception {
    DungeonLevel level = parse(LEVEL.replace("1,1;2,1", "9,9;2,1"));

    assertEquals(1, level.startTiles().size());
    assertEquals(new Coordinate(2, 1), level.startTiles().getFirst().coordinate());
  }

  @Test
  void parseLevelInvalidDesignLabelThrowsRuntimeException() {
    assertThrows(RuntimeException.class, () -> parse(LEVEL.replace("FOREST", "LAVA")));
  }

  @Test
  void parseLevelInvalidStartTilesAreSkipped() throws Exception {
    DungeonLevel level = parse(LEVEL.replace("1,1;2,1", "x,y;1;1,1"));

    assertEquals(1, level.startTiles().size());
    assertEquals(new Coordinate(1, 1), level.startTiles().getFirst().coordinate());
  }

  @Test
  void parseLevelInvalidNamedPointsAreSkipped() throws Exception {
    DungeonLevel level =
        parse(LEVEL.replace("spawn:2.5,1.5;boss:1,2", "spawn:a,b;broken;bad:1;boss:1,2"));

    assertEquals(Map.of("boss", new Point(1, 2)), level.namedPoints());
  }

  @Test
  void parseLevelInvalidDecorationsAreSkipped() throws Exception {
    DungeonLevel level =
        parse(
            LEVEL.replace(
                "SoulweaverWorkbench:1.5,1.5",
                "Unknown:1,1;SoulweaverWorkbench:x,1;SoulweaverWorkbench:1;broken"));

    assertTrue(level.decorations().isEmpty());
  }

  @Test
  void parseLevelInvalidLayoutCharacterThrowsIllegalArgumentException() {
    assertThrows(IllegalArgumentException.class, () -> parse(LEVEL.replace("WFEW", "WFXW")));
  }

  @Test
  void parseLevelShorterLayoutRowThrowsStringIndexOutOfBoundsException() {
    assertThrows(StringIndexOutOfBoundsException.class, () -> parse(LEVEL.replace("WFEW", "WFE")));
  }
}
