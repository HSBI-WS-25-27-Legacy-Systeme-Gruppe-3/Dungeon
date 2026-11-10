package engine.language;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

abstract class LocalizationTestSupport {

  @TempDir Path directory;

  final Localization localization = Localization.getInstance();
  private final Map<Language, List<TranslationFile>> savedFiles = new EnumMap<>(Language.class);
  private Language savedLanguage;

  @BeforeEach
  void saveLocalization() {
    savedLanguage = localization.currentLanguage();
    for (Language language : Language.values()) {
      List<TranslationFile> files = localization.translationFiles(language);
      savedFiles.put(language, new ArrayList<>(files));
      files.clear();
    }
    localization.currentLanguage(Language.DE);
  }

  @AfterEach
  void restoreLocalization() {
    for (Language language : Language.values()) {
      List<TranslationFile> files = localization.translationFiles(language);
      files.clear();
      files.addAll(savedFiles.get(language));
    }
    localization.currentLanguage(savedLanguage);
  }

  String writeFile(String name, String content) throws IOException {
    return Files.writeString(directory.resolve(name), content).toString().replace('\\', '/');
  }
}
