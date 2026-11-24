package engine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import com.badlogic.gdx.graphics.Color;
import engine.language.Language;
import engine.language.Localization;

class GameStarterTest {

    @Test
    void builderPreservesRequiredArguments() {
        // Arrange
        String title = "Test Game";
        Class<?> serverClass = Object.class;

        // Act
        GameStarter starter = GameStarter.builder(title, serverClass).build();

        // Assert
        assertEquals(title, starter.title());
        assertSame(serverClass, starter.serverMainClass());
    }

    @Test
    void builderRejectsInvalidTitle() {
        assertThrows(
                IllegalArgumentException.class,
                () -> GameStarter.builder(null, Object.class));

        assertThrows(
                IllegalArgumentException.class,
                () -> GameStarter.builder("", Object.class));

        assertThrows(
                IllegalArgumentException.class,
                () -> GameStarter.builder("   ", Object.class));
    }

    @Test
    void builderRejectsNullServerMainClass() {
        assertThrows(
                NullPointerException.class,
                () -> GameStarter.builder("Test Game", null));
    }

    @Test
    void builderConfiguresBackgroundImage() {
        GameStarter starter = GameStarter.builder("Test Game", Object.class)
                .backgroundImage("  images/background.png  ")
                .build();

        assertEquals(
                "images/background.png",
                starter.backgroundImage().orElseThrow());
    }

    @Test
    void backgroundImageIsEmptyByDefault() {
        GameStarter starter = GameStarter.builder("Test Game", Object.class).build();

        assertTrue(starter.backgroundImage().isEmpty());
    }

    @Test
    void backgroundImageIsEmptyForNullOrBlank() {
        GameStarter nullImage = GameStarter.builder("Test Game", Object.class)
                .backgroundImage(null)
                .build();

        GameStarter blankImage = GameStarter.builder("Test Game", Object.class)
                .backgroundImage("   ")
                .build();

        assertTrue(nullImage.backgroundImage().isEmpty());
        assertTrue(blankImage.backgroundImage().isEmpty());
    }

    @Test
    void builderConfiguresAccentColor() {
        Color accentColor = new Color(0.2f, 0.4f, 0.6f, 1.0f);

        GameStarter starter = GameStarter.builder("Test Game", Object.class)
                .accentColor(accentColor)
                .build();

        assertEquals(accentColor, starter.accentColor());
    }

    @Test
    void accentColorIsCopiedWhenBuilding() {
        Color originalColor = new Color(0.2f, 0.4f, 0.6f, 1.0f);

        GameStarter starter = GameStarter.builder("Test Game", Object.class)
                .accentColor(originalColor)
                .build();

        originalColor.set(Color.RED);

        assertEquals(
                new Color(0.2f, 0.4f, 0.6f, 1.0f),
                starter.accentColor());
    }

    @Test
    void builderConfiguresServerArguments() {
        GameStarter starter = GameStarter.builder("Test Game", Object.class)
                .serverArguments("  --server  ", "  --port=1234  ")
                .build();

        assertArrayEquals(
                new String[] { "--server", "--port=1234" },
                starter.serverArguments());
    }

    @Test
    void serverArgumentsAreDefensivelyCopied() {
        String[] arguments = { "--server", "--port=1234" };

        GameStarter starter = GameStarter.builder("Test Game", Object.class)
                .serverArguments(arguments)
                .build();

        // Ursprüngliches Array verändern
        arguments[0] = "--modified";

        // Zurückgegebenes Array verändern
        String[] returnedArguments = starter.serverArguments();
        returnedArguments[1] = "--changed";

        assertArrayEquals(
                new String[] { "--server", "--port=1234" },
                starter.serverArguments());
    }

    @Test
    void builderRejectsInvalidServerArguments() {
        assertThrows(
                NullPointerException.class,
                () -> GameStarter.builder("Test Game", Object.class)
                        .serverArguments((String[]) null));

        assertThrows(
                IllegalArgumentException.class,
                () -> GameStarter.builder("Test Game", Object.class)
                        .serverArguments());
    }

    @Test
    void builderConfiguresLocalServerPort() {
        GameStarter starter = GameStarter.builder("Test Game", Object.class)
                .localServerPort(12345)
                .build();

        assertEquals(12345, starter.localServerPort());
    }

    @Test
    void builderValidatesLocalServerPortRange() {
        // Kleinster gültiger Port
        GameStarter minimum = GameStarter.builder("Test Game", Object.class)
                .localServerPort(1)
                .build();

        // Größter gültiger Port
        GameStarter maximum = GameStarter.builder("Test Game", Object.class)
                .localServerPort(65535)
                .build();

        assertEquals(1, minimum.localServerPort());
        assertEquals(65535, maximum.localServerPort());

        // Ungültige Ports
        assertThrows(
                IllegalArgumentException.class,
                () -> GameStarter.builder("Test Game", Object.class)
                        .localServerPort(0));

        assertThrows(
                IllegalArgumentException.class,
                () -> GameStarter.builder("Test Game", Object.class)
                        .localServerPort(-1));

        assertThrows(
                IllegalArgumentException.class,
                () -> GameStarter.builder("Test Game", Object.class)
                        .localServerPort(65536));
    }

    @Test
    void builderConfiguresLanguage() {
        Language language = Localization.getInstance().currentLanguage();

        GameStarter starter = GameStarter.builder("Test Game", Object.class)
                .language(language)
                .build();

        assertSame(language, starter.language());
    }

    @Test
    void builderRejectsNullLanguage() {
        assertThrows(
                NullPointerException.class,
                () -> GameStarter.builder("Test Game", Object.class)
                        .language(null));
    }

    @Test
    void builderConfiguresLevelEditorPath() {
        GameStarter starter = GameStarter.builder("Test Game", Object.class)
                .levelEditor("levels/custom")
                .build();

        assertEquals(
                "levels/custom",
                starter.levelEditorLevelPath().orElseThrow());
    }

    @Test
    void levelEditorIsDisabledByDefault() {
        GameStarter starter = GameStarter.builder("Test Game", Object.class).build();

        assertTrue(starter.levelEditorLevelPath().isEmpty());
    }

    @Test
    void builderRejectsNullLevelEditorPath() {
        assertThrows(
                NullPointerException.class,
                () -> GameStarter.builder("Test Game", Object.class)
                        .levelEditor(null));
    }

    @Test
    void continueGameIsUnavailableByDefault() {
        GameStarter starter = GameStarter.builder("Test Game", Object.class).build();

        assertFalse(starter.continueAvailable());

        assertThrows(
                IllegalStateException.class,
                starter::continueServerArguments);
    }

    @Test
    void continueGameAvailabilityCallbackIsEvaluated() {
        boolean[] saveExists = { false };

        GameStarter starter = GameStarter.builder("Test Game", Object.class)
                .continueGame(() -> saveExists[0], "--continue")
                .build();

        assertFalse(starter.continueAvailable());

        saveExists[0] = true;

        assertTrue(starter.continueAvailable());
    }

    @Test
    void builderConfiguresContinueServerArguments() {
        GameStarter starter = GameStarter.builder("Test Game", Object.class)
                .continueGame(() -> true, "  --continue  ", "  --save=slot1  ")
                .build();

        assertArrayEquals(
                new String[] { "--continue", "--save=slot1" },
                starter.continueServerArguments());
    }

    @Test
    void continueServerArgumentsAreDefensivelyCopied() {
        String[] arguments = { "--continue", "--save=slot1" };

        GameStarter starter = GameStarter.builder("Test Game", Object.class)
                .continueGame(() -> true, arguments)
                .build();

        // Ursprüngliches Array verändern
        arguments[0] = "--modified";

        // Zurückgegebenes Array verändern
        String[] returnedArguments = starter.continueServerArguments();
        returnedArguments[1] = "--changed";

        assertArrayEquals(
                new String[] { "--continue", "--save=slot1" },
                starter.continueServerArguments());
    }

    @Test
    void builderRejectsInvalidContinueGameArguments() {
        // Fehlender Callback
        assertThrows(
                NullPointerException.class,
                () -> GameStarter.builder("Test Game", Object.class)
                        .continueGame(null, "--continue"));

        // Null als Argument-Array
        assertThrows(
                NullPointerException.class,
                () -> GameStarter.builder("Test Game", Object.class)
                        .continueGame(() -> true, (String[]) null));

        // Leeres Argument-Array
        assertThrows(
                IllegalArgumentException.class,
                () -> GameStarter.builder("Test Game", Object.class)
                        .continueGame(() -> true));
    }

    @Test
    void startupConsentIsEmptyByDefault() {
        GameStarter starter = GameStarter.builder("Test Game", Object.class).build();

        assertTrue(starter.startupConsent().isEmpty());
    }

    @Test
    void builderConfiguresStartupConsent() {
        boolean[] accepted = { false };

        GameStarter.StartupConsent consent = new GameStarter.StartupConsent(
                "Datenschutz",
                "Einwilligung erforderlich",
                "Möchtest du zustimmen?",
                "Akzeptieren",
                "Ablehnen",
                decision -> accepted[0] = decision);

        GameStarter starter = GameStarter.builder("Test Game", Object.class)
                .startupConsent(() -> consent)
                .build();

        assertEquals(consent, starter.startupConsent().orElseThrow());

        // Zustimmung simulieren
        starter.startupConsent().orElseThrow().decision().accept(true);

        assertTrue(accepted[0]);
    }

    @Test
    void builderRejectsNullStartupConsent() {
        assertThrows(
                NullPointerException.class,
                () -> GameStarter.builder("Test Game", Object.class)
                        .startupConsent(null));
    }

    @Test
    void trackingSettingsAreEmptyByDefault() {
        GameStarter starter = GameStarter.builder("Test Game", Object.class).build();

        assertTrue(starter.trackingSettings().isEmpty());
    }

    @Test
    void builderRejectsNullTrackingSettings() {
        assertThrows(
                NullPointerException.class,
                () -> GameStarter.builder("Test Game", Object.class)
                        .trackingSettings(null));
    }

    @Test
    void builderConfiguresTrackingSettings() {
        boolean[] accepted = { false };
        boolean[] deleted = { false };

        GameStarter.TrackingSettings settings = new GameStarter.TrackingSettings(
                "Tracking",
                "Datenerfassung",
                "Tracking erlauben?",
                "Deaktiviert",
                "Aktivieren",
                "Deaktivieren",
                "Daten löschen",
                "Löschen bestätigen",
                "Wirklich löschen?",
                "Daten gelöscht",
                "Löschen fehlgeschlagen",
                "Tracking aktiviert",
                "Tracking deaktiviert",
                false,
                decision -> accepted[0] = decision,
                () -> {
                    deleted[0] = true;
                    return true;
                });

        GameStarter starter = GameStarter.builder("Test Game", Object.class)
                .trackingSettings(() -> settings)
                .build();

        assertEquals(settings, starter.trackingSettings().orElseThrow());

        settings.decision().accept(true);
        assertTrue(accepted[0]);

        assertTrue(settings.deleteLocalData().getAsBoolean());
        assertTrue(deleted[0]);
    }

    @Test
    void builderConfiguresAskPlayerName() {
        GameStarter defaultStarter = GameStarter.builder("Test Game", Object.class).build();

        GameStarter configuredStarter = GameStarter.builder("Test Game", Object.class)
                .askPlayerName(false)
                .build();

        assertTrue(defaultStarter.askPlayerName());
        assertFalse(configuredStarter.askPlayerName());
    }

    @Test
    void hostActionLabelIsEmptyWhenSupplierReturnsNull() {
        GameStarter starter = GameStarter.builder("Test Game", Object.class)
                .hostActionLabel(() -> null)
                .build();

        assertTrue(starter.hostActionLabel().isEmpty());
    }

    @Test
    void builderRejectsNullHostActionLabel() {
        assertThrows(
                NullPointerException.class,
                () -> GameStarter.builder("Test Game", Object.class)
                        .hostActionLabel(null));
    }

    @Test
    void builderRejectsNullLevelEditorCallback() {
        assertThrows(
                NullPointerException.class,
                () -> GameStarter.builder("Test Game", Object.class)
                        .beforeLevelEditorStart(null));
    }
}