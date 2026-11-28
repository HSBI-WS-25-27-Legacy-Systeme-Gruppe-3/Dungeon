package testingUtils;

import engine.game.PreRunConfiguration;
import engine.network.server.CharacterSelection;
import feature.entities.CharacterClass;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Setzt vor jedem Test die Multiplayer-Einstellungen und die Charakterauswahl auf die Standardwerte
 * und stellt danach die alten Werte wieder her.
 */
public final class PreRunConfigurationReset {

  private boolean multiplayerEnabled;
  private boolean networkServer;
  private Optional<CharacterClass> characterClass;
  private Map<String, CharacterClass> assignments;

  /** Merkt sich die aktuellen Werte und setzt die Standardwerte. */
  public void before() {
    multiplayerEnabled = PreRunConfiguration.multiplayerEnabled();
    networkServer = PreRunConfiguration.isNetworkServer();
    characterClass = PreRunConfiguration.multiplayerCharacterClass();
    assignments = new LinkedHashMap<>(storedAssignments());

    PreRunConfiguration.multiplayerEnabled(false);
    PreRunConfiguration.isNetworkServer(true);
    PreRunConfiguration.multiplayerCharacterClass(null);
    CharacterSelection.clear();
  }

  /** Stellt die Werte von vor dem Test wieder her. */
  public void after() {
    PreRunConfiguration.multiplayerEnabled(multiplayerEnabled);
    PreRunConfiguration.isNetworkServer(networkServer);
    PreRunConfiguration.multiplayerCharacterClass(characterClass.orElse(null));
    CharacterSelection.restoreAssignments(assignments);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, CharacterClass> storedAssignments() {
    try {
      Field field = CharacterSelection.class.getDeclaredField("ASSIGNMENTS");
      field.setAccessible(true);
      synchronized (CharacterSelection.class) {
        return new LinkedHashMap<>((Map<String, CharacterClass>) field.get(null));
      }
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Charakterauswahl konnte nicht gelesen werden", e);
    }
  }
}
