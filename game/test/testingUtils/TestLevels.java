package testingUtils;

import engine.level.DungeonLevel;
import engine.level.loader.LevelParser;
import engine.level.loader.parsers.V2FormatParser;
import engine.level.utils.DesignLabel;
import engine.systems.LevelSystem;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Loads fixed levels for tests, so tests do not have to build their level themselves.
 *
 * <p>Available levels in {@code test_assets/testlevels/}: {@code empty_room}, {@code obstacle_room}
 * and {@code exit_room}. Call {@link #unload()} after each test.
 */
public final class TestLevels {

  private TestLevels() {}

  /**
   * Loads a level from {@code test_assets/testlevels/} and makes it the current level.
   *
   * @param name name of the level file without ".level", e.g. "empty_room"
   * @return the loaded level
   * @throws IllegalArgumentException if there is no test level with this name
   */
  public static DungeonLevel load(String name) {
    InputStream file = TestLevels.class.getResourceAsStream("/testlevels/" + name + ".level");
    if (file == null) {
      throw new IllegalArgumentException("Unknown test level: " + name);
    }
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(file, StandardCharsets.UTF_8))) {
      return makeCurrent(LevelParser.parseLevel(reader, name));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Builds a level from rows of characters and makes it the current level.
   *
   * <p>The first row is the top of the level. Characters: F floor, W wall, E exit, D door, H hole,
   * S skip.
   *
   * @param rows rows of the level, all with the same length
   * @return the built level
   */
  public static DungeonLevel fromAscii(String... rows) {
    return makeCurrent(
        new DungeonLevel(V2FormatParser.loadLevelLayout(List.of(rows)), DesignLabel.DEFAULT));
  }

  /** Removes the current level, so the next test starts without one. */
  public static void unload() {
    new LevelSystem().loadLevel(null);
  }

  private static DungeonLevel makeCurrent(DungeonLevel level) {
    // LevelSystem stores the current level globally, so no LevelSystem has to be added to the game
    new LevelSystem().loadLevel(level);
    return level;
  }
}
