package testingUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import engine.game.PreRunConfiguration;
import engine.network.server.CharacterSelection;
import feature.entities.CharacterClass;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PreRunConfigurationResetTest {

  private static final String PLAYER = "Alice";

  private final PreRunConfigurationReset reset = new PreRunConfigurationReset();

  private boolean multiplayerEnabled;
  private boolean networkServer;
  private Optional<CharacterClass> characterClass;
  private Map<String, CharacterClass> assignments;

  @BeforeEach
  void setUp() {
    multiplayerEnabled = PreRunConfiguration.multiplayerEnabled();
    networkServer = PreRunConfiguration.isNetworkServer();
    characterClass = PreRunConfiguration.multiplayerCharacterClass();
    assignments = CharacterSelection.assignments();

    PreRunConfiguration.multiplayerEnabled(true);
    PreRunConfiguration.isNetworkServer(false);
    PreRunConfiguration.multiplayerCharacterClass(CharacterClass.WIZARD);
    CharacterSelection.restoreAssignments(Map.of(PLAYER, CharacterClass.HUNTER));
  }

  @AfterEach
  void tearDown() {
    PreRunConfiguration.multiplayerEnabled(multiplayerEnabled);
    PreRunConfiguration.isNetworkServer(networkServer);
    PreRunConfiguration.multiplayerCharacterClass(characterClass.orElse(null));
    CharacterSelection.restoreAssignments(assignments);
  }

  @Test
  void setsTheDefaultValuesBeforeTheTest() {
    reset.before();

    assertFalse(PreRunConfiguration.multiplayerEnabled());
    assertTrue(PreRunConfiguration.isNetworkServer());
    assertTrue(PreRunConfiguration.multiplayerCharacterClass().isEmpty());
    assertFalse(CharacterSelection.assignments().containsKey(PLAYER));
  }

  @Test
  void restoresTheOldValuesAfterTheTest() {
    reset.before();
    PreRunConfiguration.multiplayerEnabled(false);
    PreRunConfiguration.multiplayerCharacterClass(CharacterClass.MUSHROOM_WIZARD);
    CharacterSelection.restoreAssignments(Map.of("Bob", CharacterClass.WIZARD));

    reset.after();

    assertTrue(PreRunConfiguration.multiplayerEnabled());
    assertFalse(PreRunConfiguration.isNetworkServer());
    assertEquals(
        Optional.of(CharacterClass.WIZARD), PreRunConfiguration.multiplayerCharacterClass());
    assertEquals(CharacterClass.HUNTER, CharacterSelection.assignments().get(PLAYER));
    assertFalse(CharacterSelection.assignments().containsKey("Bob"));
  }
}
