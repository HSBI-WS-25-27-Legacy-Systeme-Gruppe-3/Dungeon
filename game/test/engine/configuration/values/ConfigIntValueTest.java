package engine.configuration.values;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

// tests for @link ConfigIntValue and the value handling of @link ConfigValue
class ConfigIntValueTest {

  @Test
  void valueCanBeChanged() {
    ConfigIntValue value = new ConfigIntValue(1);
    value.value(2);
    assertEquals(2, value.value());
  }

  @Test
  void serializeReturnsNumberAsString() {
    assertEquals("42", new ConfigIntValue(42).serialize());
  }

  @Test
  void deserializeReturnsAndStoresTheValue() {
    ConfigIntValue value = new ConfigIntValue(1);
    assertEquals(42, value.deserialize("42"));
    assertEquals(42, value.value());
  }

  @Test
  void deserializeRejectsTextAndKeepsTheOldValue() {
    ConfigIntValue value = new ConfigIntValue(1);
    assertThrows(NumberFormatException.class, () -> value.deserialize("abc"));
    assertEquals(1, value.value());
  }
}
