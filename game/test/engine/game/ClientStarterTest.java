package engine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ClientStarterTest {

  @Test
  void initLocalizationRejectsNull() {
    // Arrange
    ClientStarter.Builder builder = ClientStarter.builder(() -> {});

    // Act & Assert
    assertThrows(NullPointerException.class, () -> builder.initLocalization(null));
  }

  @Test
  void registerSettingsRejectsNull() {
    // Arrange
    ClientStarter.Builder builder = ClientStarter.builder(() -> {});

    // Act & Assert
    assertThrows(NullPointerException.class, () -> builder.registerSettings(null));
  }

  @Test
  void clientInheritsServerConfiguration() {
    // Arrange
    ServerStarter server = ServerStarter.builder(() -> {}).port(7777).frameRate(60).build();

    // Act
    ClientStarter client = ClientStarter.builder(server, () -> {}).build();

    // Assert
    assertEquals(7777, client.port);
  }
}
