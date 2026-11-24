package engine.level.loader.parsers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.graphics.Color;
import engine.level.DungeonLevel;
import engine.level.utils.Coordinate;
import engine.level.utils.DesignLabel;
import engine.level.utils.LevelElement;
import engine.rendering.Atmosphere;
import engine.utils.Point;
import engine.utils.Tuple;
import feature.entities.deco.Deco;
import feature.prefabs.PrefabInstance;
import java.io.BufferedReader;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Tests for {@link V3FormatParser} class. */
public class V3FormatParserTest {

  // default layout:
  //
  // y=2  W F W W
  // y=1  W F E W
  // y=0  W W W W

  private static final String LEVEL_NAME = "v3formatparsertest";

  private static final String LEVEL =
      """
      {
        "version": 3,
        "design": "ICE",
        "layout": ["WFWW", "WFEW", "WWWW"],
        "startTiles": [{ "x": 1, "y": 1 }, { "x": 2, "y": 1 }],
        "namedPoints": { "spawn": { "x": 2.5, "y": 1.5 }, "boss": { "x": 1, "y": 2 } },
        "decorations": [{ "type": "SoulweaverWorkbench", "position": { "x": 1.5, "y": 1.5 } }],
        "prefabs": []
      }
      """;

  private static final ObjectMapper MAPPER = JsonMapper.builder().build();

  private final V3FormatParser parser = new V3FormatParser();
  private DungeonLevel level;

  @BeforeEach
  void setup() {
    LevelElement[][] layout =
        new LevelElement[][] {
          {LevelElement.WALL, LevelElement.WALL, LevelElement.WALL, LevelElement.WALL},
          {LevelElement.WALL, LevelElement.FLOOR, LevelElement.EXIT, LevelElement.WALL},
          {LevelElement.WALL, LevelElement.FLOOR, LevelElement.WALL, LevelElement.WALL},
        };

    level =
        new DungeonLevel(
            layout,
            DesignLabel.FIRE,
            Map.of("spawn", new Point(2.5f, 1.5f)),
            List.of(new Tuple<>(Deco.SoulweaverWorkbench, new Point(1.5f, 1.5f))));
    level.startTiles().add(level.layout()[1][1]);
    level.atmosphere(Atmosphere.Preset.DAYLIGHT, Optional.of(Color.valueOf("a0b0c0")));
    level.addPrefab(new PrefabInstance("torch1", "torch", Map.of()));
  }

  private DungeonLevel parse(String levelData) throws Exception {
    return parser.parseLevel(new BufferedReader(new StringReader(levelData)), LEVEL_NAME);
  }

  private static LevelElement element(DungeonLevel level, int x, int y) {
    return level.layout()[y][x].levelElement();
  }

  private static String level(String layout, String startTiles) {
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

  @Test
  void serializeLevelWithPrefabsHappyPath() {
    String serialized = parser.serializeLevel(level, level.prefabs());
    JsonNode root = MAPPER.readTree(serialized);

    assertEquals(
        List.of(
            "version",
            "design",
            "atmosphere",
            "layout",
            "startTiles",
            "namedPoints",
            "decorations",
            "prefabs"),
        List.copyOf(root.propertyNames()));
    assertEquals(3, root.get("version").intValue());
    assertEquals("FIRE", root.get("design").asText());
    assertEquals("DAYLIGHT", root.get("atmosphere").get("preset").asText());
    assertEquals("a0b0c0", root.get("atmosphere").get("ambient").asText());
    assertEquals(
        List.of("WFWW", "WFEW", "WWWW"),
        root.get("layout").valueStream().map(JsonNode::asText).toList());
    assertEquals(1, root.get("startTiles").size());
    assertEquals(1.0, root.get("startTiles").get(0).get("x").doubleValue());
    assertEquals(1.0, root.get("startTiles").get(0).get("y").doubleValue());
    assertEquals(2.5, root.get("namedPoints").get("spawn").get("x").doubleValue());
    assertEquals(1.5, root.get("namedPoints").get("spawn").get("y").doubleValue());
    assertEquals("SoulweaverWorkbench", root.get("decorations").get(0).get("type").asText());
    assertEquals(1.5, root.get("decorations").get(0).get("position").get("x").doubleValue());
    assertEquals(1, root.get("prefabs").size());
    assertEquals("torch1", root.get("prefabs").get(0).get("name").asText());
    assertEquals("torch", root.get("prefabs").get(0).get("type").asText());
    assertTrue(root.get("prefabs").get(0).get("properties").isObject());
  }

  @Test
  void serializeLevelWithPrefabsNullLevelThrowsIllegalArgumentException() {
    assertThrows(IllegalArgumentException.class, () -> parser.serializeLevel(null, List.of()));
  }

  @Test
  void serializeLevelHappyPath() {
    assertEquals(parser.serializeLevel(level, level.prefabs()), parser.serializeLevel(level));
  }

  @Test
  void serializeLevelNullLevelThrowsIllegalArgumentException() {
    assertThrows(IllegalArgumentException.class, () -> parser.serializeLevel(null));
  }

  @Test
  void parseLevelHappyPath() throws Exception {
    DungeonLevel parsed = parse(LEVEL);

    assertSame(DungeonLevel.class, parsed.getClass());
    assertEquals(DesignLabel.ICE, parsed.baseDesignLabel());
    assertEquals(3, parsed.layout().length);
    assertEquals(4, parsed.layout()[0].length);
    assertEquals(LevelElement.FLOOR, element(parsed, 1, 2));
    assertEquals(LevelElement.EXIT, element(parsed, 2, 1));
    assertEquals(LevelElement.WALL, element(parsed, 1, 0));
    assertEquals(2, parsed.startTiles().size());
    assertEquals(new Coordinate(1, 1), parsed.startTiles().get(0).coordinate());
    assertEquals(new Coordinate(2, 1), parsed.startTiles().get(1).coordinate());
    assertEquals(
        Map.of("spawn", new Point(2.5f, 1.5f), "boss", new Point(1, 2)), parsed.namedPoints());
    assertEquals(1, parsed.decorations().size());
    assertEquals(Deco.SoulweaverWorkbench, parsed.decorations().getFirst().a());
    assertEquals(new Point(1.5f, 1.5f), parsed.decorations().getFirst().b());
    assertEquals(Atmosphere.Preset.NEUTRAL, parsed.atmospherePreset());
    assertTrue(parsed.prefabs().isEmpty());
  }

  @Test
  void parseLevelStartTileWithoutTileThrowsIllegalArgumentException() {
    assertThrows(
        IllegalArgumentException.class,
        () -> parse(LEVEL.replace("{ \"x\": 1, \"y\": 1 }", "{ \"x\": 9, \"y\": 9 }")));
  }

  private static Stream<String> invalidLevels() {
    return Stream.of(
        level("[]", "[]"),
        level("[\"\"]", "[]"),
        level("[1]", "[]"),
        level("[\"WFW\", \"WF\"]", "[]"),
        level("[\"WXW\"]", "[]"),
        level("\"WFW\"", "[]"),
        level("[\"WFW\"]", "{}"),
        level("[\"WFW\"]", "[1]"),
        level("[\"WFW\"]", "[{ \"x\": \"1\", \"y\": 0 }]"),
        level("[\"WFW\"]", "[{ \"x\": 1 }]"),
        level("[\"WFW\"]", "[{ \"x\": 1, \"y\": 0, \"z\": 0 }]"),
        LEVEL.replace("\"ICE\"", "\"LAVA\""),
        LEVEL.replace("\"ICE\"", "1"),
        LEVEL.replace(
            "{ \"spawn\": { \"x\": 2.5, \"y\": 1.5 }, \"boss\": { \"x\": 1, \"y\": 2 } }", "[]"),
        LEVEL.replace("\"spawn\": { \"x\": 2.5, \"y\": 1.5 }", "\" \": { \"x\": 2.5, \"y\": 1.5 }"),
        LEVEL.replace("\"spawn\": { \"x\": 2.5, \"y\": 1.5 }", "\"spawn\": 1"),
        LEVEL.replace("\"SoulweaverWorkbench\"", "\"Unknown\""),
        LEVEL.replace(
            "[{ \"type\": \"SoulweaverWorkbench\", \"position\": { \"x\": 1.5, \"y\": 1.5 } }]",
            "[1]"),
        LEVEL.replace(", \"position\": { \"x\": 1.5, \"y\": 1.5 }", ""),
        LEVEL.replace("\"version\": 3", "\"version\": 2"),
        LEVEL.replace("\"prefabs\": []", "\"prefabs\": [], \"foo\": 1"),
        "[]");
  }

  @ParameterizedTest
  @MethodSource("invalidLevels")
  void parseLevelInvalidDataThrowsIllegalArgumentException(String levelData) {
    assertThrows(IllegalArgumentException.class, () -> parse(levelData));
  }

  @Test
  void parseLevelInvalidJsonThrowsIllegalArgumentException() {
    assertThrows(IllegalArgumentException.class, () -> parse("{ \"version\": 3, "));
  }
}
