package engine.level.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import engine.Entity;
import engine.Game;
import engine.components.PlayerComponent;
import engine.components.PositionComponent;
import engine.level.DungeonLevel;
import engine.level.elements.ILevel;
import engine.level.utils.DesignLabel;
import engine.level.utils.LevelElement;
import engine.resources.Resources;
import engine.systems.LevelSystem;
import engine.utils.IVoidFunction;
import engine.utils.Point;
import engine.utils.Tuple;
import feature.level.MissingLevelException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for {@link DungeonLoader} class. */
public class DungeonLoaderTest {

  // levels:
  //   "loadertesta": variant 0 = FIRE (start 1,1), variant 1 = ICE (start 2,1)
  //   "loadertestb": variant 0 = FOREST (start 1,1)
  //   "loadertestmissing": missing level file

  private static final String LEVEL_A = "loadertesta";
  private static final String LEVEL_B = "loadertestb";
  private static final String LEVEL_MISSING = "loadertestmissing";

  @TempDir Path tempDir;

  private Map<String, List<String>> levels;
  private Map<String, List<String>> originalLevels;
  private IVoidFunction originalAfterAllLevels;
  private Entity player;
  private int afterAllLevelsCalls;

  @BeforeEach
  void setup() throws Exception {
    Resources.remove(ILevel.class);
    Game.add(new LevelSystem());
    player = new Entity();
    player.add(new PositionComponent(0, 0));
    player.add(new PlayerComponent());
    Game.add(player);

    levels = levelsMap();
    originalLevels = new HashMap<>(levels);
    originalAfterAllLevels = (IVoidFunction) staticField("afterAllLevels").get(null);
    DungeonLoader.clearLevelOrder();
    DungeonLoader.afterAllLevels(() -> afterAllLevelsCalls++);

    levels.put(
        LEVEL_A,
        new ArrayList<>(
            List.of(levelFile("a_0.level", "FIRE", 1, 1), levelFile("a_1.level", "ICE", 2, 1))));
    levels.put(LEVEL_B, new ArrayList<>(List.of(levelFile("b_0.level", "FOREST", 1, 1))));
  }

  @AfterEach
  void cleanup() {
    levels.clear();
    levels.putAll(originalLevels);
    DungeonLoader.clearLevelOrder();
    DungeonLoader.afterAllLevels(originalAfterAllLevels);
    Game.removeAllEntities();
    Game.removeAllSystems();
    Resources.remove(ILevel.class);
  }

  private static Field staticField(String name) throws NoSuchFieldException {
    Field field = DungeonLoader.class.getDeclaredField(name);
    field.setAccessible(true);

    return field;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, List<String>> levelsMap() throws Exception {
    return (Map<String, List<String>>) staticField("LEVELS").get(null);
  }

  private String levelFile(String fileName, String design, int startX, int startY)
      throws IOException {
    String content =
        """
        {
          "version": 3,
          "design": "%s",
          "layout": ["WWWW", "WFFW", "WWWW"],
          "startTiles": [{ "x": %d, "y": %d }],
          "namedPoints": {},
          "decorations": [],
          "prefabs": []
        }
        """
            .formatted(design, startX, startY);
    Path file = tempDir.resolve(fileName);
    Files.writeString(file, content);

    return file.toString();
  }

  private static void register(String... names) {
    for (String name : names) {
      DungeonLoader.addLevel(new Tuple<>(name, DungeonLevel.class));
    }
  }

  private static DungeonLevel currentGameLevel() {
    return (DungeonLevel) Game.currentLevel().orElseThrow();
  }

  private static DungeonLevel inMemoryLevel() {
    return new DungeonLevel(
        new LevelElement[][] {{LevelElement.FLOOR, LevelElement.FLOOR}}, DesignLabel.TEMPLE);
  }

  private Point playerPosition() {
    return player.fetch(PositionComponent.class).orElseThrow().position();
  }

  @Test
  void addLevelStoresLowercaseNamesWithHandler() {
    DungeonLoader.addLevel(
        new Tuple<>("LoaderTestA", DungeonLevel.class),
        new Tuple<>("LOADERTESTB", LevelParserTest.TestLevelHandler.class));

    assertEquals(List.of(LEVEL_A, LEVEL_B), DungeonLoader.levelOrder());
    assertEquals(2, DungeonLoader.levelCount());
    assertEquals(Optional.of(DungeonLevel.class), DungeonLoader.levelHandler(LEVEL_A));
    assertEquals(
        Optional.of(LevelParserTest.TestLevelHandler.class), DungeonLoader.levelHandler(LEVEL_B));
  }

  @Test
  void levelOrderReturnsAllNamesInOrder() {
    register(LEVEL_B);
    register(LEVEL_A, LEVEL_MISSING);

    assertEquals(List.of(LEVEL_B, LEVEL_A, LEVEL_MISSING), DungeonLoader.levelOrder());
  }

  @Test
  void currentLevelReturnsNameAtCurrentIndex() {
    register(LEVEL_A, LEVEL_B);

    DungeonLoader.loadInMemoryLevel(LEVEL_B, inMemoryLevel());

    assertEquals(1, DungeonLoader.currentLevelIndex());
    assertEquals(LEVEL_B, DungeonLoader.currentLevel());
  }

  @Test
  void levelHandlerReturnsHandlerOfLevel() {
    DungeonLoader.addLevel(
        new Tuple<>(LEVEL_A, DungeonLevel.class),
        new Tuple<>(LEVEL_B, LevelParserTest.TestLevelHandler.class));

    assertEquals(
        Optional.of(LevelParserTest.TestLevelHandler.class), DungeonLoader.levelHandler(LEVEL_B));
    assertEquals(
        Optional.of(LevelParserTest.TestLevelHandler.class),
        DungeonLoader.levelHandler("LoaderTestB"));
  }

  @Test
  void levelHandlerUnknownNameReturnsEmpty() {
    register(LEVEL_A);
    assertEquals(Optional.empty(), DungeonLoader.levelHandler("unknown"));
  }

  @Test
  void levelHandlerNullReturnsEmpty() {
    register(LEVEL_A);
    assertEquals(Optional.empty(), DungeonLoader.levelHandler(null));
  }

  @Test
  void loadNextLevelLoadsLevelsInOrder() {
    register(LEVEL_A, LEVEL_B);

    DungeonLoader.loadNextLevel();
    int variant = DungeonLoader.currentVariantIndex().orElseThrow();

    assertEquals(0, DungeonLoader.currentLevelIndex());
    assertTrue(variant == 0 || variant == 1);
    assertEquals(
        variant == 0 ? DesignLabel.FIRE : DesignLabel.ICE, currentGameLevel().baseDesignLabel());
    assertEquals(
        Optional.of(levels.get(LEVEL_A).get(variant)), DungeonLoader.currentLevelAssetPath());

    DungeonLoader.loadNextLevel();

    assertEquals(1, DungeonLoader.currentLevelIndex());
    assertEquals(OptionalInt.of(0), DungeonLoader.currentVariantIndex());
    assertEquals(DesignLabel.FOREST, currentGameLevel().baseDesignLabel());
    assertEquals(0, afterAllLevelsCalls);
  }

  @Test
  void loadNextLevelAfterLastLevelExecutesAfterAllLevels() {
    register(LEVEL_B);
    DungeonLoader.loadNextLevel();
    DungeonLevel lastLevel = currentGameLevel();

    DungeonLoader.loadNextLevel();

    assertEquals(1, afterAllLevelsCalls);
    assertEquals(1, DungeonLoader.currentLevelIndex());
    assertSame(lastLevel, currentGameLevel());
  }

  @Test
  void loadNextLevelWithoutLevelFileExecutesAfterAllLevels() {
    register(LEVEL_MISSING);

    DungeonLoader.loadNextLevel();

    assertEquals(1, afterAllLevelsCalls);
    assertEquals(0, DungeonLoader.currentLevelIndex());
    assertTrue(Game.currentLevel().isEmpty());
  }

  @Test
  void loadLevelByNameSetsCurrentLevel() {
    register(LEVEL_A, LEVEL_B);

    DungeonLoader.loadLevel(LEVEL_B);

    assertEquals(1, DungeonLoader.currentLevelIndex());
    assertEquals(OptionalInt.of(0), DungeonLoader.currentVariantIndex());
    assertEquals(DesignLabel.FOREST, currentGameLevel().baseDesignLabel());
  }

  @Test
  void loadLevelByNameNullThrowsMissingLevelException() {
    register(LEVEL_A);

    assertThrows(MissingLevelException.class, () -> DungeonLoader.loadLevel((String) null));

    assertEquals(-1, DungeonLoader.currentLevelIndex());
  }

  @Test
  void loadInMemoryLevelSetsCurrentLevel() {
    register(LEVEL_A, LEVEL_B);
    DungeonLoader.loadLevel(LEVEL_A, 0);
    DungeonLevel level = inMemoryLevel();

    DungeonLoader.loadInMemoryLevel(LEVEL_B, level);

    assertEquals(1, DungeonLoader.currentLevelIndex());
    assertSame(level, currentGameLevel());
    assertEquals(OptionalInt.empty(), DungeonLoader.currentVariantIndex());
    assertEquals(Optional.empty(), DungeonLoader.currentLevelAssetPath());
  }

  @Test
  void loadInMemoryLevelNullArgumentsThrowNullPointerException() {
    register(LEVEL_A);

    assertThrows(
        NullPointerException.class, () -> DungeonLoader.loadInMemoryLevel(null, inMemoryLevel()));
    assertThrows(NullPointerException.class, () -> DungeonLoader.loadInMemoryLevel(LEVEL_A, null));
  }

  @Test
  void loadInMemoryLevelUnregisteredNameThrowsMissingLevelException() {
    register(LEVEL_A);

    assertThrows(
        MissingLevelException.class,
        () -> DungeonLoader.loadInMemoryLevel("unknown", inMemoryLevel()));

    assertTrue(Game.currentLevel().isEmpty());
  }

  @Test
  void loadLevelByIndexSetsCurrentLevel() {
    register(LEVEL_A, LEVEL_B);

    DungeonLoader.loadLevel(1);

    assertEquals(1, DungeonLoader.currentLevelIndex());
    assertEquals(DesignLabel.FOREST, currentGameLevel().baseDesignLabel());
  }

  @Test
  void loadLevelByIndexOutOfBoundsThrowsIndexOutOfBoundsException() {
    register(LEVEL_A, LEVEL_B);

    assertThrows(IndexOutOfBoundsException.class, () -> DungeonLoader.loadLevel(-1));
    assertThrows(IndexOutOfBoundsException.class, () -> DungeonLoader.loadLevel(2));
  }

  @Test
  void loadLevelByVariantSetsCurrentLevelAndPlayerPosition() {
    register(LEVEL_B, LEVEL_A);

    DungeonLoader.loadLevel(LEVEL_A, 1);

    assertEquals(1, DungeonLoader.currentLevelIndex());
    assertEquals(OptionalInt.of(1), DungeonLoader.currentVariantIndex());
    assertEquals(Optional.of(levels.get(LEVEL_A).get(1)), DungeonLoader.currentLevelAssetPath());
    assertEquals(DesignLabel.ICE, currentGameLevel().baseDesignLabel());
    assertEquals(new Point(2.5f, 1.5f), playerPosition());
  }

  @Test
  void loadLevelByVariantInvalidVariantThrowsMissingLevelException() {
    register(LEVEL_A);
    assertThrows(MissingLevelException.class, () -> DungeonLoader.loadLevel(LEVEL_A, 2));
  }

  @Test
  void loadLevelByVariantWithoutLevelFileThrowsMissingLevelException() {
    register(LEVEL_A, LEVEL_MISSING);
    assertThrows(MissingLevelException.class, () -> DungeonLoader.loadLevel(LEVEL_MISSING, 0));
  }

  @Test
  void reloadCurrentLevelReloadsLevelAndResetsPlayerPosition() {
    register(LEVEL_B, LEVEL_A);
    DungeonLoader.loadLevel(LEVEL_A, 0);
    DungeonLevel loadedLevel = currentGameLevel();
    player.fetch(PositionComponent.class).orElseThrow().position(new Point(2.5f, 1.5f));

    DungeonLoader.reloadCurrentLevel();

    assertEquals(1, DungeonLoader.currentLevelIndex());
    assertEquals(OptionalInt.of(0), DungeonLoader.currentVariantIndex());
    assertNotSame(loadedLevel, currentGameLevel());
    assertEquals(DesignLabel.FIRE, currentGameLevel().baseDesignLabel());
    assertEquals(new Point(1.5f, 1.5f), playerPosition());
  }

  @Test
  void reloadCurrentLevelInvalidIndexThrowsIndexOutOfBoundsException() {
    register(LEVEL_B);

    assertThrows(IndexOutOfBoundsException.class, DungeonLoader::reloadCurrentLevel);

    DungeonLoader.loadNextLevel();
    DungeonLoader.loadNextLevel();
    assertThrows(IndexOutOfBoundsException.class, DungeonLoader::reloadCurrentLevel);
  }

  @Test
  void reloadCurrentLevelWithoutVariantThrowsIllegalStateException() {
    register(LEVEL_A);
    DungeonLoader.loadInMemoryLevel(LEVEL_A, inMemoryLevel());

    assertThrows(IllegalStateException.class, DungeonLoader::reloadCurrentLevel);
  }

  @Test
  void clearLevelOrderResetsState() {
    register(LEVEL_A, LEVEL_B);
    DungeonLoader.loadLevel(LEVEL_B, 0);

    DungeonLoader.clearLevelOrder();

    assertEquals(List.of(), DungeonLoader.levelOrder());
    assertEquals(-1, DungeonLoader.currentLevelIndex());
    assertEquals(OptionalInt.empty(), DungeonLoader.currentVariantIndex());
    assertEquals(Optional.empty(), DungeonLoader.currentLevelAssetPath());
    assertEquals(1, levels.get(LEVEL_B).size());
  }

  @Test
  void clearLevelsRemovesLevelFilesAndLevelOrder() {
    register(LEVEL_A, LEVEL_B);
    DungeonLoader.loadLevel(LEVEL_B, 0);

    DungeonLoader.clearLevels();

    assertTrue(levels.isEmpty());
    assertEquals(List.of(), DungeonLoader.levelOrder());
    assertEquals(-1, DungeonLoader.currentLevelIndex());
  }
}
