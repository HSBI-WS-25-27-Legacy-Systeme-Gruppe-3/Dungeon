package testingUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import engine.Entity;
import engine.Game;
import engine.level.DungeonLevel;
import engine.level.elements.ILevel;
import engine.level.loader.DungeonLoader;
import engine.systems.LevelSystem;
import engine.utils.Tuple;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.mockito.Mockito;

/**
 * Tests for {@link CleanGameExtension}.
 *
 * <p>The scenario classes below contain a test that fails on purpose. They are disabled, so that
 * they do not fail the build. The tests in this class start them through the JUnit launcher with
 * {@code @Disabled} switched off and check the result.
 */
@CleanGame
class CleanGameExtensionTest {

  private static final String DELIBERATE_FAILURE = "fails on purpose to check the reset";
  private static final String DISABLED_REASON = "only started by CleanGameExtensionTest";

  @Test
  void resetsGameStateBeforeAndAfterEachTestEvenIfTheTestFails() {
    // Leftovers, like a test without @CleanGame would leave them behind.
    leaveGameStateBehind();

    TestExecutionSummary summary = runScenario(ResetScenario.class);

    assertEquals(2, summary.getTestsStartedCount(), () -> describe(summary));
    assertEquals(1, summary.getTestsSucceededCount(), () -> describe(summary));
    assertEquals(1, summary.getTestsFailedCount(), () -> describe(summary));
    assertEquals(DELIBERATE_FAILURE, summary.getFailures().getFirst().getException().getMessage());
    // The failing test ran last, so only the reset after it can have cleared its state.
    assertGameStateIsClean();
  }

  @Test
  void runsResetsAroundEachTestAndRestoresThemInReverseOrder() {
    HookScenario.LOG.clear();

    TestExecutionSummary summary = runScenario(HookScenario.class);

    assertEquals(1, summary.getTestsFailedCount(), () -> describe(summary));
    assertEquals(
        List.of(
            "first.before",
            "second.before",
            "failing test",
            "second.after with 0 entities",
            "first.after with 0 entities",
            "first.before",
            "second.before",
            "passing test",
            "second.after with 0 entities",
            "first.after with 0 entities"),
        HookScenario.LOG);
  }

  private static void leaveGameStateBehind() {
    Game.add(new Entity());
    Game.add(new LevelSystem());
    Game.currentLevel(Mockito.mock(ILevel.class));
    DungeonLoader.addLevel(new Tuple<>("leftover", DungeonLevel.class));
  }

  private static void assertGameStateIsClean() {
    assertEquals(0, Game.entities().count(), "entities");
    assertTrue(Game.systems().isEmpty(), "systems");
    assertTrue(Game.currentLevel().isEmpty(), "current level");
    assertTrue(DungeonLoader.levelOrder().isEmpty(), "level order");
  }

  private static TestExecutionSummary runScenario(final Class<?> scenario) {
    LauncherDiscoveryRequest request =
        LauncherDiscoveryRequestBuilder.request()
            .selectors(DiscoverySelectors.selectClass(scenario))
            .configurationParameter(
                "junit.jupiter.conditions.deactivate", "org.junit.*DisabledCondition")
            .build();
    SummaryGeneratingListener listener = new SummaryGeneratingListener();
    LauncherFactory.create().execute(request, listener);
    return listener.getSummary();
  }

  private static String describe(final TestExecutionSummary summary) {
    return summary.getFailures().stream()
        .map(f -> f.getTestIdentifier().getDisplayName() + ": " + f.getException())
        .collect(Collectors.joining("\n", "Failures:\n", ""));
  }

  /** Starts with leftovers of an earlier test and leaves state behind in a failing last test. */
  @CleanGame
  @Disabled(DISABLED_REASON)
  @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
  static class ResetScenario {

    @Test
    @Order(1)
    void startsWithoutLeftoversOfEarlierTests() {
      assertGameStateIsClean();
    }

    @Test
    @Order(2)
    void leavesGameStateBehindAndFails() {
      leaveGameStateBehind();
      fail(DELIBERATE_FAILURE);
    }
  }

  /** Records when the resets run around a failing and a passing test. */
  @Disabled(DISABLED_REASON)
  @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
  static class HookScenario {

    static final List<String> LOG = new ArrayList<>();

    @RegisterExtension
    static final CleanGameExtension CLEAN_GAME =
        new CleanGameExtension(
            () -> List.of(new RecordingReset("first"), new RecordingReset("second")));

    @Test
    @Order(1)
    void failingTest() {
      LOG.add("failing test");
      Game.add(new Entity());
      fail(DELIBERATE_FAILURE);
    }

    @Test
    @Order(2)
    void passingTest() {
      LOG.add("passing test");
    }
  }

  /**
   * Writes each call into the log, after a test together with the number of entities left.
   *
   * @param name the name of this reset in the log
   */
  private record RecordingReset(String name) implements TestStateReset {

    @Override
    public void before() {
      HookScenario.LOG.add(name + ".before");
    }

    @Override
    public void after() {
      HookScenario.LOG.add(name + ".after with " + Game.entities().count() + " entities");
    }
  }
}
