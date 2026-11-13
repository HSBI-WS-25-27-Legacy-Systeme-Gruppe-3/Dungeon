package engine.resources;

import java.util.Optional;

/**
 * Handle to a global resource, for example the currently loaded level.
 *
 * <p>Systems keep a {@code Res} instead of the resource itself. The handle resolves the resource on
 * every call to {@link #get()}, so it does not become stale when the resource is replaced at
 * runtime, for example after a level change.
 *
 * <p>Handles for the game are provided by {@link Resources}. Tests can create a fixed handle with
 * {@link #of(Object)} or {@link #empty()} instead of setting global game state.
 *
 * @param <T> type of the resource
 */
@FunctionalInterface
public interface Res<T> {

  /**
   * Resolves the current value of the resource.
   *
   * @return the current value, or an empty {@link Optional} if the resource is not available, for
   *     example because no level is loaded yet
   */
  Optional<T> get();

  /**
   * Creates a handle that always resolves to the given value.
   *
   * <p>Intended for tests that should run against a fixed or mocked resource.
   *
   * @param value the value the handle resolves to; {@code null} behaves like {@link #empty()}
   * @param <T> type of the resource
   * @return a handle that always resolves to {@code value}
   */
  static <T> Res<T> of(final T value) {
    return () -> Optional.ofNullable(value);
  }

  /**
   * Creates a handle for a resource that is not available.
   *
   * <p>Intended for tests that check how a system behaves without the resource, for example without
   * a loaded level.
   *
   * @param <T> type of the resource
   * @return a handle that always resolves to an empty {@link Optional}
   */
  static <T> Res<T> empty() {
    return Optional::empty;
  }
}
