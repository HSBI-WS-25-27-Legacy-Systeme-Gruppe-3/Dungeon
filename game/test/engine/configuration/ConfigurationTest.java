package engine.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import engine.configuration.values.ConfigIntValue;
import engine.utils.JsonHandler;
import engine.utils.components.path.IPath;
import engine.utils.components.path.SimpleIPath;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// tests for loading, repairing and saving a @link Configuration
class ConfigurationTest {

  private static final int DEFAULT_SPEED = 5;
  private static final int DEFAULT_VOLUME = 50;

  // keys without a @link ConfigMap, so several Configuration instances can share them
  static final class TestConfig {
    static final ConfigKey<Integer> SPEED =
        new ConfigKey<>(new String[] {"player", "speed"}, new ConfigIntValue(DEFAULT_SPEED));
    static final ConfigKey<Integer> VOLUME =
        new ConfigKey<>(new String[] {"volume"}, new ConfigIntValue(DEFAULT_VOLUME));
  }

  // only loaded once: every load adds the @link ConfigMap prefix to the static keys again
  @ConfigMap(path = {"keyboard"})
  static final class PrefixedConfig {
    static final ConfigKey<Integer> JUMP =
        new ConfigKey<>(new String[] {"jump"}, new ConfigIntValue(1));
  }

  @TempDir Path tempDir;
  private Path file;
  private IPath path;

  @BeforeEach
  void setUp() {
    file = tempDir.resolve("config.json");
    path = new SimpleIPath(file.toString());
    // the keys are static and keep their value between tests
    TestConfig.SPEED.value.value(DEFAULT_SPEED);
    TestConfig.VOLUME.value.value(DEFAULT_VOLUME);
  }

  private Map<String, Object> readFile() throws IOException {
    return JsonHandler.readJson(Files.readString(file));
  }

  private String savedSpeed() throws IOException {
    return JsonHandler.getValueByPath(readFile(), "player", "speed");
  }

  private void load() throws IOException {
    Configuration.loadAndGetConfiguration(path, TestConfig.class);
  }

  @Test
  void missingFileIsCreatedWithDefaults() throws IOException {
    load();
    assertTrue(Files.exists(file));
    assertEquals("5", savedSpeed());
    assertEquals(DEFAULT_SPEED, TestConfig.SPEED.value());
  }

  @Test
  void emptyFileIsFilledWithDefaults() throws IOException {
    Files.writeString(file, "   ");
    load();
    assertEquals("5", savedSpeed());
  }

  @Test
  void brokenJsonIsReplacedByDefaults() throws IOException {
    Files.writeString(file, "{ this is not json");
    load();
    assertEquals("5", savedSpeed());
  }

  @Test
  void malformedJsonObjectIsReplacedByDefaults() throws IOException {
    Files.writeString(file, "{\"player\":}");
    load();
    assertEquals("5", savedSpeed());
  }

  @Test
  void jsonArrayIsReplacedByDefaults() throws IOException {
    Files.writeString(file, "[1, 2, 3]");
    load();
    assertEquals("5", savedSpeed());
  }

  @Test
  void valuesFromFileAreAppliedToKeys() throws IOException {
    Files.writeString(file, "{\"player\":{\"speed\":\"9\"},\"volume\":\"70\"}");
    load();
    assertEquals(9, TestConfig.SPEED.value());
    assertEquals(70, TestConfig.VOLUME.value());
  }

  @Test
  void jsonNumberIsAcceptedLikeAString() throws IOException {
    Files.writeString(file, "{\"player\":{\"speed\":9},\"volume\":70}");
    load();
    assertEquals(9, TestConfig.SPEED.value());
  }

  @Test
  void completeFileIsNotRewritten() throws IOException {
    String content = "{\"player\":{\"speed\":\"9\"},\"volume\":\"70\"}";
    Files.writeString(file, content);
    load();
    assertEquals(content, Files.readString(file));
  }

  @Test
  void missingKeyIsAddedWithoutTouchingExistingValues() throws IOException {
    Files.writeString(file, "{\"volume\":\"70\"}");
    load();
    assertEquals(70, TestConfig.VOLUME.value());
    assertEquals(DEFAULT_SPEED, TestConfig.SPEED.value());
    assertEquals("5", savedSpeed());
    assertEquals("70", JsonHandler.getValueByPath(readFile(), "volume"));
  }

  @Test
  void nullValueIsReplacedByDefault() throws IOException {
    Files.writeString(file, "{\"player\":{\"speed\":null},\"volume\":\"70\"}");
    load();
    assertEquals(DEFAULT_SPEED, TestConfig.SPEED.value());
    assertEquals("5", savedSpeed());
  }

  @Test
  void invalidValueIsReplacedByDefault() throws IOException {
    Files.writeString(file, "{\"player\":{\"speed\":\"fast\"},\"volume\":\"70\"}");
    load();
    assertEquals(DEFAULT_SPEED, TestConfig.SPEED.value());
    assertEquals("5", savedSpeed());
    assertEquals(70, TestConfig.VOLUME.value());
  }

  @Test
  void unknownKeysInFileSurviveSaving() throws IOException {
    Files.writeString(file, "{\"foreign\":\"keep me\"}");
    load();
    assertEquals("keep me", JsonHandler.getValueByPath(readFile(), "foreign"));
  }

  @Test
  void settingAValueWritesItToTheFile() throws IOException {
    load();
    TestConfig.SPEED.value(42);
    assertEquals(42, TestConfig.SPEED.value());
    assertEquals("42", savedSpeed());
  }

  @Test
  void savedValueIsLoadedAgain() throws IOException {
    load();
    TestConfig.SPEED.value(42);
    TestConfig.SPEED.value.value(DEFAULT_SPEED); // forget the value in memory
    // a second path, so that this test does not depend on how the cache compares paths
    Path copy = tempDir.resolve("copy.json");
    Files.copy(file, copy);
    Configuration.loadAndGetConfiguration(new SimpleIPath(copy.toString()), TestConfig.class);
    assertEquals(42, TestConfig.SPEED.value());
  }

  @Test
  void samePathInstanceReturnsCachedConfiguration() throws IOException {
    Configuration first = Configuration.loadAndGetConfiguration(path, TestConfig.class);
    Configuration second = Configuration.loadAndGetConfiguration(path, TestConfig.class);
    assertSame(first, second);
  }

  @Test
  void configMapPathIsUsedAsPrefix() throws IOException {
    Configuration.loadAndGetConfiguration(path, PrefixedConfig.class);
    assertEquals("1", JsonHandler.getValueByPath(readFile(), "keyboard", "jump"));
  }
}
