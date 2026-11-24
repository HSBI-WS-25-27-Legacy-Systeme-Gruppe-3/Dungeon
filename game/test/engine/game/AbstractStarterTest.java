package engine.game;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AbstractStarterTest {

    @Test
    void portRejectsZero() {
        // Arrange
        ServerStarter.Builder builder = ServerStarter.builder(() -> {
        });

        // Act & Assert
        assertThrows(
                IllegalArgumentException.class,
                () -> builder.port(0));
    }

    @Test
    void portRejectsValuesAbove65535() {
        // Arrange
        ServerStarter.Builder builder = ServerStarter.builder(() -> {
        });

        // Act & Assert
        assertThrows(
                IllegalArgumentException.class,
                () -> builder.port(65536));
    }

    @Test
    void portAcceptsValidBoundaryValues() {
        // Arrange
        ServerStarter.Builder builder = ServerStarter.builder(() -> {
        });

        // Act & Assert
        assertDoesNotThrow(() -> builder.port(1));
        assertDoesNotThrow(() -> builder.port(65535));
    }

    @Test
    void frameRateRejectsZeroAndNegativeValues() {
        // Arrange
        ServerStarter.Builder builder = ServerStarter.builder(() -> {
        });

        // Act & Assert
        assertThrows(
                IllegalArgumentException.class,
                () -> builder.frameRate(0));

        assertThrows(
                IllegalArgumentException.class,
                () -> builder.frameRate(-1));
    }

    @Test
    void frameRateAcceptsPositiveValues() {
        // Arrange
        ServerStarter.Builder builder = ServerStarter.builder(() -> {
        });

        // Act & Assert
        assertDoesNotThrow(() -> builder.frameRate(1));
        assertDoesNotThrow(() -> builder.frameRate(60));
        assertDoesNotThrow(() -> builder.frameRate(144));
    }

    @Test
    void builderRejectsNullSetupCallback() {
        // Act & Assert
        assertThrows(
                NullPointerException.class,
                () -> ServerStarter.builder(null));
    }

    @Test
    void onConfigureRejectsNull() {
        // Arrange
        ServerStarter.Builder builder = ServerStarter.builder(() -> {
        });

        // Act & Assert
        assertThrows(
                NullPointerException.class,
                () -> builder.onConfigure(null));
    }

    @Test
    void builtStarterIsNotAffectedByLaterBuilderChanges() {
        // Arrange
        ServerStarter.Builder builder = ServerStarter.builder(() -> {
        }).port(7777);

        ServerStarter starter = builder.build();

        // Act
        builder.port(8888);

        // Assert
        assertEquals(7777, starter.port);
    }
}