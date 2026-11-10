package engine.language;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import engine.utils.JsonHandler;
import java.io.IOException;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@Isolated("Uses the global localization registry")
class TranslationTest extends LocalizationTestSupport {

  @BeforeEach
  void registerTranslations() throws IOException {
    localization.registerTranslationFile(
        Language.DE,
        writeFile(
            "de.json",
            """
            {"dialog": {"hello": "Hallo", "onlyGerman": "Nur Deutsch"}}
            """));
    localization.registerTranslationFile(
        Language.EN,
        writeFile(
            "en.json",
            """
            {"dialog": {"hello": "Hello"}}
            """));
  }

  @Test
  void resolvesKeysWithAndWithoutBasePath() {
    assertEquals("Hallo", new Translation().text("dialog.hello"));
    assertEquals("Hallo", new Translation("dialog").text("hello"));
    assertEquals("Hallo", new Translation(null).text("dialog.hello"));
  }

  @Test
  void followsCurrentLanguageUnlessExplicitlyOverridden() {
    Translation translation = new Translation("dialog");
    localization.currentLanguage(Language.EN);
    assertEquals("Hello", translation.text("hello"));

    translation.language(Language.DE);
    assertEquals("Hallo", translation.text("hello"));

    translation.language(null);
    assertEquals("Hello", translation.text("hello"));
    localization.currentLanguage(Language.DE);
    assertEquals("Hallo", translation.text("hello"));
  }

  @Test
  void fallsBackToGermanAndMarksMissingKeysWithFullPath() {
    localization.currentLanguage(Language.EN);

    assertEquals("Nur Deutsch", new Translation("dialog").text("onlyGerman"));
    assertEquals("{dialog.missing}", new Translation("dialog").text("missing"));
    assertEquals("{missing}", new Translation().text("missing"));
  }

  @Test
  void usesLatestFileButKeepsEarlierKeysAsFallback() throws IOException {
    localization.registerTranslationFile(
        Language.DE,
        writeFile(
            "override.json",
            """
            {"dialog": {"hello": "Guten Tag"}}
            """));

    Translation translation = new Translation("dialog");
    assertEquals("Guten Tag", translation.text("hello"));
    assertEquals("Nur Deutsch", translation.text("onlyGerman"));
  }

  @Test
  void lookupUsesRequestedLanguageWithoutGermanFallback() {
    Translation translation = new Translation("dialog");

    assertEquals("Hello", translation.lookup(Language.EN, "hello"));
    assertNull(translation.lookup(Language.EN, "onlyGerman"));
  }

  @ParameterizedTest
  @MethodSource("templates")
  void substitutesPlaceholdersLiterally(String template, Object[] arguments, String expected)
      throws IOException {
    localization.registerTranslationFile(
        Language.DE,
        writeFile("template.json", JsonHandler.writeJson(Map.of("template", template))));

    assertEquals(expected, new Translation().text("template", arguments));
  }

  private static Stream<Arguments> templates() {
    return Stream.of(
        Arguments.of("$1 / $2 / $1", new Object[] {"Ada", "Bob"}, "Ada / Bob / Ada"),
        Arguments.of("$1 / $2", new Object[] {"Ada"}, "Ada / $2"),
        Arguments.of("$1 / $2", new Object[0], "$1 / $2"),
        Arguments.of("$$1 / $1", new Object[] {"Ada"}, "$1 / Ada"),
        Arguments.of("$$1", new Object[0], "$1"),
        Arguments.of("$1", new Object[] {"$2"}, "$2"),
        Arguments.of("$1", new Object[] {"C:\\temp\\$5"}, "C:\\temp\\$5"));
  }
}
