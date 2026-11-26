package engine.game;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import feature.entities.CharacterClass;
import org.junit.jupiter.api.Test;

class ServerStarterTest {

  @Test
  void maximumPlayersRejectsZeroAndNegativeValues() {
    // Arrange
    ServerStarter.Builder builder = ServerStarter.builder(() -> {});

    // Act & Assert
    assertThrows(IllegalArgumentException.class, () -> builder.maximumPlayers(0));

    assertThrows(IllegalArgumentException.class, () -> builder.maximumPlayers(-1));
  }

  @Test
  void maximumPlayersAcceptsPositiveValues() {
    // Arrange
    ServerStarter.Builder builder = ServerStarter.builder(() -> {});

    // Act & Assert
    assertDoesNotThrow(() -> builder.maximumPlayers(1));
    assertDoesNotThrow(() -> builder.maximumPlayers(4));
    assertDoesNotThrow(() -> builder.maximumPlayers(100));
  }

  @Test
  void primaryCharacterClassDefaultsToWizard() {
    // Arrange
    ServerStarter starter = ServerStarter.builder(() -> {}).build();

    // Act
    CharacterClass result = starter.primaryCharacterClass();

    // Assert
    assertEquals(CharacterClass.WIZARD, result);
  }

  @Test
  void primaryCharacterClassUsesConfiguredClass() {
    // Arrange
    ServerStarter starter =
        ServerStarter.builder(() -> {}).characterClasses(CharacterClass.HUNTER).build();

    // Act
    CharacterClass result = starter.primaryCharacterClass();

    // Assert
    assertEquals(CharacterClass.HUNTER, result);
  }

  @Test
  void characterClassesRejectsNull() {
    // Arrange
    ServerStarter.Builder builder = ServerStarter.builder(() -> {});

    // Act & Assert
    assertThrows(
        NullPointerException.class, () -> builder.characterClasses((CharacterClass[]) null));
  }
}
