package testingUtils;

/**
 * Resets one part of the global state for tests annotated with {@link CleanGame}.
 *
 * <p>Implementations are found with {@link java.util.ServiceLoader}: add the fully qualified class
 * name to {@code test_assets/META-INF/services/testingUtils.TestStateReset}. An implementation
 * needs a public constructor without parameters. A new instance is created for every test, so an
 * implementation can save the original values in {@link #before()} and restore them in {@link
 * #after()}.
 *
 * <p>Order: {@link #before()} runs for all resets in the order of the service file, before {@link
 * CleanGameExtension} clears the game state. {@link #after()} runs in reverse order, after the game
 * state has been cleared.
 */
public interface TestStateReset {

  /** Runs before each test. Typically saves the current values and sets defined start values. */
  void before();

  /**
   * Runs after each test, even if the test failed. Typically restores the values saved in {@link
   * #before()}.
   */
  default void after() {}
}
