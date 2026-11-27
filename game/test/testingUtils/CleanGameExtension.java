package testingUtils;

import engine.Game;
import engine.level.loader.DungeonLoader;
import engine.systems.LevelSystem;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.function.Supplier;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * JUnit extension behind {@link CleanGame}.
 *
 * <p>Before each test, {@link TestStateReset#before()} runs for all registered resets, then the
 * game state is cleared. After each test, also if it failed, the game state is cleared first and
 * then {@link TestStateReset#after()} runs in reverse order. If one of these steps fails after a
 * test, the remaining steps still run and the first error is rethrown.
 */
public final class CleanGameExtension implements BeforeEachCallback, AfterEachCallback {

  private static final ExtensionContext.Namespace NAMESPACE =
      ExtensionContext.Namespace.create(CleanGameExtension.class);

  private final Supplier<List<TestStateReset>> resetFactory;

  /** Creates the extension with all resets registered in the service file. */
  public CleanGameExtension() {
    this(CleanGameExtension::loadRegisteredResets);
  }

  /**
   * Creates the extension with the given resets instead of the registered ones.
   *
   * @param resetFactory creates new reset instances for each test
   */
  CleanGameExtension(final Supplier<List<TestStateReset>> resetFactory) {
    this.resetFactory = resetFactory;
  }

  @Override
  public void beforeEach(final ExtensionContext context) {
    ActiveResets active = new ActiveResets(new ArrayList<>());
    context.getStore(NAMESPACE).put(ActiveResets.class, active);
    for (TestStateReset reset : resetFactory.get()) {
      reset.before();
      // Only resets whose before() has run are restored after the test.
      active.resets().add(reset);
    }
    clearGameState();
  }

  @Override
  public void afterEach(final ExtensionContext context) {
    ActiveResets active =
        context.getStore(NAMESPACE).remove(ActiveResets.class, ActiveResets.class);
    List<Runnable> steps = new ArrayList<>();
    steps.add(CleanGameExtension::clearGameState);
    if (active != null) {
      List<TestStateReset> resets = active.resets();
      for (int i = resets.size() - 1; i >= 0; i--) {
        steps.add(resets.get(i)::after);
      }
    }
    runAll(steps);
  }

  /**
   * Clears the game state that every test can change: entities, systems, the current level and the
   * level order of the {@link DungeonLoader}.
   */
  static void clearGameState() {
    // Entities first, so that the systems are still informed about the removed entities.
    Game.removeAllEntities();
    Game.removeAllSystems();
    if (LevelSystem.level().isPresent()) {
      // A new LevelSystem also works if none is registered and does not call the onLevelLoad
      // callback of a test.
      new LevelSystem().loadLevel(null);
    }
    // Only the level order is cleared. The level files stay known, because the DungeonLoader
    // searches for them only once, when the class is loaded.
    DungeonLoader.clearLevelOrder();
  }

  private static List<TestStateReset> loadRegisteredResets() {
    List<TestStateReset> resets = new ArrayList<>();
    ServiceLoader.load(TestStateReset.class).forEach(resets::add);
    return resets;
  }

  private static void runAll(final List<Runnable> steps) {
    Throwable failure = null;
    for (Runnable step : steps) {
      try {
        step.run();
      } catch (RuntimeException | AssertionError e) {
        if (failure == null) failure = e;
        else failure.addSuppressed(e);
      }
    }
    if (failure instanceof RuntimeException runtimeException) throw runtimeException;
    if (failure instanceof AssertionError assertionError) throw assertionError;
  }

  private record ActiveResets(List<TestStateReset> resets) {}
}
