package engine.configuration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import engine.configuration.values.ConfigIntValue;
import org.junit.jupiter.api.Test;

// tests for @link ConfigKey
class ConfigKeyTest {

  @Test
  void pathIsConvertedToLowerCase() {
    ConfigKey<Integer> key =
        new ConfigKey<>(new String[] {"Movement", "UP"}, new ConfigIntValue(1));
    assertArrayEquals(new String[] {"movement", "up"}, key.path);
  }

  @Test
  void returnsDefaultValueUntilChanged() {
    ConfigKey<Integer> key = new ConfigKey<>(new String[] {"a"}, new ConfigIntValue(1));
    assertEquals(1, key.value());
  }

  @Test
  void valueCanBeChangedWithoutAConfiguration() {
    ConfigKey<Integer> key = new ConfigKey<>(new String[] {"a"}, new ConfigIntValue(1));
    key.value(2);
    assertEquals(2, key.value());
  }
}
