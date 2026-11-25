package engine.language;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.badlogic.gdx.utils.GdxRuntimeException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Isolated("Reads files through the global Gdx.files service")
class TranslationFileTest {

  @TempDir Path directory;

  @Test
  void readsNestedKeysAndUtf8AndReturnsNullForMissingKeys() throws IOException {
    Path path = directory.resolve("de.json");
    Files.writeString(path, "{\"dialog\": {\"hello\": \"Grüße aus Minden\"}}");
    TranslationFile file = new TranslationFile(path.toString());

    assertEquals("Grüße aus Minden", file.value(new String[] {"dialog", "hello"}));
    assertNull(file.value(new String[] {"missing"}));
    assertNull(file.value(new String[] {"dialog", "missing"}));
    assertNull(file.value(new String[] {"dialog", "hello", "child"}));
  }

  @Test
  void loadsOnlyOnFirstAccessAndReusesContentAfterFileChanges() throws IOException {
    Path path = directory.resolve("lazy.json");
    TranslationFile file = assertDoesNotThrow(() -> new TranslationFile(path.toString()));

    Files.writeString(path, "{\"hello\": \"Original\"}");
    assertEquals("Original", file.value(new String[] {"hello"}));

    Files.writeString(path, "{\"hello\": \"Changed\"}");
    assertEquals("Original", file.value(new String[] {"hello"}));
    Files.delete(path);
    assertEquals("Original", file.value(new String[] {"hello"}));
  }

  @Test
  void missingFileFailsOnFirstRead() {
    TranslationFile file =
        assertDoesNotThrow(() -> new TranslationFile(directory.resolve("missing.json").toString()));

    assertThrows(GdxRuntimeException.class, () -> file.value(new String[] {"hello"}));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "not json", "{\"hello\":}", "{\"hello\": \"unclosed}"})
  void malformedFileFailsOnFirstRead(String json) throws IOException {
    Path path = directory.resolve("invalid.json");
    Files.writeString(path, json);
    TranslationFile file = assertDoesNotThrow(() -> new TranslationFile(path.toString()));

    assertThrows(IllegalArgumentException.class, () -> file.value(new String[] {"hello"}));
  }
}
