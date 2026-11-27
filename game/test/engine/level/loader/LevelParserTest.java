package engine.level.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import engine.level.DungeonLevel;
import engine.level.utils.Coordinate;
import engine.level.utils.DesignLabel;
import engine.level.utils.LevelElement;
import engine.rendering.Atmosphere;
import engine.utils.Point;
import engine.utils.Tuple;
import feature.entities.deco.Deco;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for {@link LevelParser} class. */
public class LevelParserTest {

  // default layout:
  //
  // W F W W
  // W F E W
  // W W W W

  private static final String LEVEL_NAME = "levelparsertest";

  private static final String V1_LEVEL =
      """
      FIRE
      1,1
      1,1;2,1
      WFWW
      WFEW
      WWWW
      """;

  private static final String V2_LEVEL =
      """
      Version: 2
      FOREST
      1,1;2,1
      spawn:2.5,1.5;boss:1,2
      SoulweaverWorkbench:1.5,1.5
      WFWW
      WFEW
      WWWW
      """;

  private static final String V3_LEVEL =
      """
      {
        "version": 3,
        "design": "ICE",
        "atmosphere": { "preset": "DAYLIGHT" },
        "layout": ["WFWW", "WFEW", "WWWW"],
        "startTiles": [{ "x": 1, "y": 1 }],
        "namedPoints": { "spawn": { "x": 2.5, "y": 1.5 } },
        "decorations": [{ "type": "SoulweaverWorkbench", "position": { "x": 1.5, "y": 1.5 } }],
        "prefabs": []
      }
      """;

  @AfterEach
  void cleanup() {
    DungeonLoader.clearLevelOrder();
  }

  private static DungeonLevel parse(String levelData) {
    return LevelParser.parseLevel(new BufferedReader(new StringReader(levelData)), LEVEL_NAME);
  }

  private static LevelElement element(DungeonLevel level, int x, int y) {
    return level.layout()[y][x].levelElement();
  }

  private static String v3(String layout, String startTiles) {
    return """
        {
          "version": 3,
          "design": "DEFAULT",
          "layout": %s,
          "startTiles": %s,
          "namedPoints": {},
          "decorations": [],
          "prefabs": []
        }
        """
        .formatted(layout, startTiles);
  }

  /** Level handler used to check that a registered handler class is instantiated. */
  public static class TestLevelHandler extends DungeonLevel {
    /**
     * Constructor looked up via reflection by the parsers.
     *
     * @param layout layout
     * @param designLabel design
     * @param namedPoints named points
     * @param decorations decorations
     */
    public TestLevelHandler(
        LevelElement[][] layout,
        DesignLabel designLabel,
        Map<String, Point> namedPoints,
        List<Tuple<Deco, Point>> decorations) {
      super(layout, designLabel, namedPoints, decorations);
    }
  }

  @Test
  void parseLevelV1HappyPath() {
    DungeonLevel level = parse(V1_LEVEL);

    assertSame(DungeonLevel.class, level.getClass());
    assertEquals(DesignLabel.FIRE, level.baseDesignLabel());
    assertEquals(3, level.layout().length);
    assertEquals(4, level.layout()[0].length);
    assertEquals(LevelElement.FLOOR, element(level, 1, 0));
    assertEquals(LevelElement.EXIT, element(level, 2, 1));
    assertEquals(LevelElement.WALL, element(level, 1, 2));
    assertEquals(new Coordinate(1, 1), level.startTile().orElseThrow().coordinate());
    assertEquals(Map.of("Point0", new Point(1, 1), "Point1", new Point(2, 1)), level.namedPoints());
    assertTrue(level.decorations().isEmpty());
  }

  @Test
  void parseLevelV2HappyPath() {
    DungeonLevel level = parse(V2_LEVEL);

    assertSame(DungeonLevel.class, level.getClass());
    assertEquals(DesignLabel.FOREST, level.baseDesignLabel());
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
  void parseLevelV3HappyPath() {
    DungeonLevel level = parse(V3_LEVEL);

    assertSame(DungeonLevel.class, level.getClass());
    assertEquals(DesignLabel.ICE, level.baseDesignLabel());
    assertEquals(Atmosphere.Preset.DAYLIGHT, level.atmospherePreset());
    assertTrue(level.ambientOverride().isEmpty());
    assertEquals(LevelElement.FLOOR, element(level, 1, 2));
    assertEquals(LevelElement.EXIT, element(level, 2, 1));
    assertEquals(LevelElement.WALL, element(level, 1, 0));
    assertEquals(new Coordinate(1, 1), level.startTile().orElseThrow().coordinate());
    assertEquals(Map.of("spawn", new Point(2.5f, 1.5f)), level.namedPoints());
    assertEquals(Deco.SoulweaverWorkbench, level.decorations().getFirst().a());
    assertTrue(level.prefabs().isEmpty());
  }

  @Test
  void parseLevelV3WithLeadingWhitespaceIsDetected() {
    DungeonLevel level = parse("\n   \t" + V3_LEVEL);
    assertEquals(DesignLabel.ICE, level.baseDesignLabel());
  }

  @Test
  void parseLevelV2WithLeadingCommentIsDetected() {
    DungeonLevel level = parse("# a comment\n" + V2_LEVEL);
    assertEquals(DesignLabel.FOREST, level.baseDesignLabel());
  }

  @Test
  void parseLevelUsesRegisteredLevelHandler() {
    DungeonLoader.addLevel(new Tuple<>(LEVEL_NAME, TestLevelHandler.class));

    assertInstanceOf(TestLevelHandler.class, parse(V1_LEVEL));
    assertInstanceOf(TestLevelHandler.class, parse(V2_LEVEL));
    assertInstanceOf(TestLevelHandler.class, parse(V3_LEVEL));
  }

  @Test
  void parseLevelFromStringEqualsReaderOverload() {
    DungeonLevel fromString = LevelParser.parseLevel(V3_LEVEL, LEVEL_NAME);
    DungeonLevel fromReader = parse(V3_LEVEL);

    assertEquals(LevelParser.serializeLevel(fromReader), LevelParser.serializeLevel(fromString));
  }

  @Test
  void serializedLevelCanBeParsedAgain() {
    DungeonLevel level = parse(V2_LEVEL);

    String serialized = LevelParser.serializeLevel(level);
    DungeonLevel reparsed = parse(serialized);

    assertTrue(serialized.stripLeading().startsWith("{"));
    assertEquals(serialized, LevelParser.serializeLevel(reparsed));
  }

  @Test
  void parseLevelEmptyStringIsParsedAsV1AndFails() {
    RuntimeException e = assertThrows(RuntimeException.class, () -> parse(""));
    assertSame(RuntimeException.class, e.getClass());
  }

  @Test
  void parseLevelNullStringThrowsNullPointerException() {
    assertThrows(
        NullPointerException.class, () -> LevelParser.parseLevel((String) null, LEVEL_NAME));
  }

  @Test
  void parseLevelNullReaderThrowsNullPointerException() {
    assertThrows(
        NullPointerException.class,
        () -> LevelParser.parseLevel((BufferedReader) null, LEVEL_NAME));
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 0, 3, 4, 99})
  void parseLevelUnsupportedVersionThrowsIllegalArgumentException(int version) {
    assertThrows(
        IllegalArgumentException.class,
        () -> parse(LevelParser.getVersion(version) + "\nDEFAULT\n1,1\n\n\nWFW\n"));
  }

  @Test
  void parseLevelNonNumericVersionFallsBackToV1() {
    RuntimeException e = assertThrows(RuntimeException.class, () -> parse("Version: abc\n"));
    assertSame(RuntimeException.class, e.getClass());
  }

  @Test
  void parseLevelReadErrorThrowsIllegalArgumentException() {
    BufferedReader failing =
        new BufferedReader(new StringReader("")) {
          @Override
          public String readLine() throws IOException {
            throw new IOException("boom");
          }
        };

    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class, () -> LevelParser.parseLevel(failing, LEVEL_NAME));

    assertInstanceOf(UncheckedIOException.class, e.getCause());
  }

  @Test
  void parseLevelV1InvalidDesignLabelThrowsRuntimeException() {
    assertThrows(RuntimeException.class, () -> parse(V1_LEVEL.replace("FIRE", "LAVA")));
  }

  @Test
  void parseLevelV1InvalidPlayerPositionThrowsRuntimeException() {
    assertThrows(RuntimeException.class, () -> parse(V1_LEVEL.replace("\n1,1\n", "\n1\n")));
  }

  @Test
  void parseLevelV1PlayerPositionOutsideLevelThrowsRuntimeException() {
    RuntimeException e =
        assertThrows(RuntimeException.class, () -> parse(V1_LEVEL.replace("\n1,1\n", "\n9,9\n")));

    assertSame(RuntimeException.class, e.getClass());
  }

  @Test
  void parseLevelV2InvalidLayoutCharacterThrowsIllegalArgumentException() {
    assertThrows(IllegalArgumentException.class, () -> parse(V2_LEVEL.replace("WFEW", "WFXW")));
  }

  @Test
  void parseLevelV2WithoutLayoutThrowsNoSuchElementException() {
    assertThrows(NoSuchElementException.class, () -> parse("Version: 2\nDEFAULT\n1,1\n\n\n"));
  }

  @Test
  void parseLevelV2ShorterLayoutRowThrowsIndexOutOfBounds() {
    assertThrows(
        StringIndexOutOfBoundsException.class, () -> parse(V2_LEVEL.replace("WFEW", "WFE")));
  }

  @Test
  void parseLevelV2IgnoresMalformedEntries() {
    DungeonLevel level =
        parse(
            """
            Version: 2
            DEFAULT
            1,1;x,y;9,9
            spawn:a,b;boss:1,2;broken
            Unknown:1,1;SoulweaverWorkbench:x,1
            WFWW
            WFEW
            WWWW
            """);

    assertEquals(1, level.startTiles().size());
    assertEquals(Map.of("boss", new Point(1, 2)), level.namedPoints());
    assertTrue(level.decorations().isEmpty());
  }

  @Test
  void parseLevelV3InvalidJsonThrowsIllegalArgumentException() {
    assertThrows(IllegalArgumentException.class, () -> parse("{ \"version\": 3, "));
  }

  private static Stream<String> invalidV3Levels() {
    return Stream.of(
        V3_LEVEL.replace("\"version\": 3", "\"version\": 2"),
        V3_LEVEL.replace("\"ICE\"", "\"LAVA\""),
        V3_LEVEL.replace("\"prefabs\": []", "\"prefabs\": [], \"foo\": 1"),
        V3_LEVEL.replace("\"namedPoints\": { \"spawn\": { \"x\": 2.5, \"y\": 1.5 } },", ""),
        v3("[]", "[]"),
        v3("[\"\"]", "[]"),
        v3("[\"WFW\", \"WF\"]", "[]"),
        v3("[\"WXW\"]", "[]"),
        v3("[\"WFW\"]", "[{ \"x\": 9, \"y\": 9 }]"),
        v3("[\"WFW\"]", "[{ \"x\": \"1\", \"y\": 0 }]"),
        V3_LEVEL.replace("\"DAYLIGHT\"", "\"FOO\""));
  }

  @ParameterizedTest
  @MethodSource("invalidV3Levels")
  void parseLevelV3InvalidDataThrowsIllegalArgumentException(String levelData) {
    assertThrows(IllegalArgumentException.class, () -> parse(levelData));
  }
}
