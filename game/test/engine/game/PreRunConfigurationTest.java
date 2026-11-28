package engine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PreRunConfigurationTest {

  @Test
  void networkServerMaximumPlayersRejectsInvalidValues() {
    // Act & Assert
    assertThrows(
        IllegalArgumentException.class, () -> PreRunConfiguration.networkServerMaximumPlayers(0));

    assertThrows(
        IllegalArgumentException.class, () -> PreRunConfiguration.networkServerMaximumPlayers(-1));
  }

  @Test
  void networkServerMaximumPlayersAcceptsValidValues() {
    // Arrange
    int originalValue = PreRunConfiguration.networkServerMaximumPlayers();

    try {
      // Act
      PreRunConfiguration.networkServerMaximumPlayers(1);

      // Assert
      assertEquals(1, PreRunConfiguration.networkServerMaximumPlayers());

      // Act
      PreRunConfiguration.networkServerMaximumPlayers(10);

      // Assert
      assertEquals(10, PreRunConfiguration.networkServerMaximumPlayers());

    } finally {
      // Ursprünglichen Wert wiederherstellen
      PreRunConfiguration.networkServerMaximumPlayers(originalValue);
    }
  }

  @Test
  void usernameRejectsNullAndBlankValues() {
    // Act & Assert
    assertThrows(IllegalArgumentException.class, () -> PreRunConfiguration.username(null));

    assertThrows(IllegalArgumentException.class, () -> PreRunConfiguration.username(""));

    assertThrows(IllegalArgumentException.class, () -> PreRunConfiguration.username("   "));
  }

  @Test
  void usernameRejectsUnderscores() {
    // Act & Assert
    assertThrows(IllegalArgumentException.class, () -> PreRunConfiguration.username("Player_1"));

    assertThrows(IllegalArgumentException.class, () -> PreRunConfiguration.username("_Player"));

    assertThrows(IllegalArgumentException.class, () -> PreRunConfiguration.username("Player_"));
  }

  @Test
  void usernameAcceptsValidValue() {
    // Arrange
    String originalUsername = PreRunConfiguration.username();

    try {
      // Act
      PreRunConfiguration.username("Player123");

      // Assert
      assertEquals("Player123", PreRunConfiguration.username());

    } finally {
      // Ursprünglichen Benutzernamen wiederherstellen
      PreRunConfiguration.username(originalUsername);
    }
  }

  @Test
  void networkServerAddressTrimsWhitespace() {
    // Arrange
    String originalAddress = PreRunConfiguration.networkServerAddress();

    try {
      // Act
      PreRunConfiguration.networkServerAddress("  localhost  ");

      // Assert
      assertEquals("localhost", PreRunConfiguration.networkServerAddress());

      assertTrue(PreRunConfiguration.hasNetworkServerAddress());

    } finally {
      // Ursprüngliche Adresse wiederherstellen
      PreRunConfiguration.networkServerAddress(originalAddress);
    }
  }

  @Test
  void networkServerAddressHandlesEmptyValues() {
    // Arrange
    String originalAddress = PreRunConfiguration.networkServerAddress();

    try {
      // Act
      PreRunConfiguration.networkServerAddress("   ");

      // Assert
      assertEquals("", PreRunConfiguration.networkServerAddress());
      assertFalse(PreRunConfiguration.hasNetworkServerAddress());

      // Act
      PreRunConfiguration.networkServerAddress(null);

      // Assert
      assertEquals("", PreRunConfiguration.networkServerAddress());
      assertFalse(PreRunConfiguration.hasNetworkServerAddress());

    } finally {
      // Ursprüngliche Adresse wiederherstellen
      PreRunConfiguration.networkServerAddress(originalAddress);
    }
  }
}
