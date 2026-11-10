package engine.language;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class LocalizedTextTest {

  @Test
  void selectsRequestedLanguage() {
    LocalizedText text = new LocalizedText(Map.of("de", "Hallo", "en", "Hello"));

    assertEquals("Hallo", text.text(Language.DE));
    assertEquals("Hello", text.text(Language.EN));
  }

  @Test
  void fallsBackToEitherAvailableLanguage() {
    assertEquals("Hallo", new LocalizedText(Map.of("de", "Hallo")).text(Language.EN));
    assertEquals("Hello", new LocalizedText(Map.of("en", "Hello")).text(Language.DE));
  }

  @Test
  void normalizesLanguageCodesAndTrimsTexts() {
    LocalizedText text = new LocalizedText(Map.of("DE", "  Guten Tag\n", "eN", "\tGood day  "));

    assertEquals(Map.of("de", "Guten Tag", "en", "Good day"), text.values());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t\n"})
  void rejectsMissingOrBlankTranslation(String value) {
    Map<String, String> values = new HashMap<>();
    values.put("de", value);

    assertThrows(IllegalArgumentException.class, () -> new LocalizedText(values));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"fr", "de-DE"})
  void rejectsUnsupportedLanguageCode(String code) {
    Map<String, String> values = new HashMap<>();
    values.put(code, "Hallo");

    assertThrows(IllegalArgumentException.class, () -> new LocalizedText(values));
  }

  @Test
  void rejectsNullOrEmptyMapAndNullRequestedLanguage() {
    assertThrows(NullPointerException.class, () -> new LocalizedText(null));
    assertThrows(IllegalArgumentException.class, () -> new LocalizedText(Map.of()));
    LocalizedText text = new LocalizedText(Map.of("de", "Hallo"));
    assertThrows(NullPointerException.class, () -> text.text(null));
  }

  @Test
  void copiesInputMapAndExposesUnmodifiableValues() {
    Map<String, String> values = new HashMap<>(Map.of("de", "Hallo"));
    LocalizedText text = new LocalizedText(values);

    values.put("de", "Geändert");
    values.put("en", "Hello");

    assertEquals(Map.of("de", "Hallo"), text.values());
    assertEquals("Hallo", text.text(Language.EN));
    assertThrows(UnsupportedOperationException.class, () -> text.values().put("en", "Hello"));
  }
}
