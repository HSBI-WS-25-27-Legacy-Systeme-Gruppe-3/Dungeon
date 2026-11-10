package engine.language;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

import com.badlogic.gdx.graphics.Texture;
import engine.utils.components.draw.TextureMap;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockMakers;

@Isolated("Uses global localization and texture state")
class LocalizationTest extends LocalizationTestSupport {

  private final List<Consumer<Language>> listeners = new ArrayList<>();
  private final List<String> texturePaths = new ArrayList<>();

  @AfterEach
  void removeTestListenersAndTextures() {
    listeners.forEach(localization::removeLanguageChangeListener);
    texturePaths.forEach(path -> TextureMap.instance().remove(path));
  }

  @Test
  void notifiesListenersOnlyWhenLanguageChanges() {
    List<Language> changes = new ArrayList<>();
    listen(changes::add);

    localization.currentLanguage(Language.DE);
    assertEquals(List.of(), changes);
    localization.currentLanguage(Language.EN);
    localization.currentLanguage(Language.EN);
    localization.currentLanguage(Language.DE);

    assertEquals(List.of(Language.EN, Language.DE), changes);
  }

  @Test
  void removedListenerReceivesNoMoreNotifications() {
    List<Language> changes = new ArrayList<>();
    Consumer<Language> listener = changes::add;
    listen(listener);
    localization.currentLanguage(Language.EN);

    localization.removeLanguageChangeListener(listener);
    localization.currentLanguage(Language.DE);

    assertEquals(List.of(Language.EN), changes);
  }

  @Test
  void listenerCanUnregisterItselfDuringNotification() {
    List<Language> selfChanges = new ArrayList<>();
    List<Language> otherChanges = new ArrayList<>();
    Consumer<Language> selfRemoving =
        new Consumer<>() {
          @Override
          public void accept(Language language) {
            selfChanges.add(language);
            localization.removeLanguageChangeListener(this);
          }
        };
    listen(selfRemoving);
    listen(otherChanges::add);

    localization.currentLanguage(Language.EN);
    localization.currentLanguage(Language.DE);

    assertEquals(List.of(Language.EN), selfChanges);
    assertEquals(List.of(Language.EN, Language.DE), otherChanges);
  }

  @Test
  void registeredFilesAreAvailableThroughLocalizationShortcuts() throws IOException {
    localization.registerTranslationFile(
        Language.DE, writeFile("de.json", "{\"hello\": \"Hallo $1\"}"));
    localization.registerTranslationFile(
        Language.EN, writeFile("en.json", "{\"hello\": \"Hello $1\"}"));

    assertEquals("Hallo $1", localization.text("hello"));
    assertEquals("Hallo Ada", localization.text("hello", "Ada"));
    localization.currentLanguage(Language.EN);
    assertEquals("Hello Ada", localization.text("hello", "Ada"));
  }

  @Test
  void assetPrefersCurrentLanguageFileOverGermanFile() throws IOException {
    localization.currentLanguage(Language.EN);
    String english = writeFile("book_en.png", "");
    writeFile("book_de.png", "");

    assertEquals(english, localization.asset(assetPath()));
  }

  @Test
  void assetFallsBackToGermanFile() throws IOException {
    localization.currentLanguage(Language.EN);
    String german = writeFile("book_de.png", "");

    assertEquals(german, localization.asset(assetPath()));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void assetReturnsBasePathWhenNeitherLocalizedVariantExists(boolean baseExists)
      throws IOException {
    localization.currentLanguage(Language.EN);
    if (baseExists) {
      writeFile("book.png", "");
    }

    assertEquals(assetPath(), localization.asset(assetPath()));
  }

  @ParameterizedTest
  @ValueSource(strings = {"en", "de"})
  void assetFindsCurrentOrFallbackVariantInTextureMap(String language) {
    localization.currentLanguage(Language.EN);
    String path = directory.resolve("book_" + language + ".png").toString().replace('\\', '/');
    assertFalse(Files.exists(directory.resolve("book_" + language + ".png")));
    texturePaths.add(path);
    TextureMap.instance()
        .put(path, mock(Texture.class, withSettings().mockMaker(MockMakers.SUBCLASS)));

    assertEquals(path, localization.asset(assetPath()));
  }

  private void listen(Consumer<Language> listener) {
    listeners.add(listener);
    localization.registerLanguageChangeListener(listener);
  }

  private String assetPath() {
    return directory.resolve("book.png").toString().replace('\\', '/');
  }
}
