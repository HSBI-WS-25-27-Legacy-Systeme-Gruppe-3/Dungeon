package engine.resources;

import engine.level.elements.ILevel;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry for global resources.
 *
 * <p>A resource is state that exists once in the game, for example the currently loaded level. Each
 * resource is stored under its type, so there is at most one value per type.
 *
 * <p><b>Reading:</b> Systems do not read resources directly. Instead, they request a {@link Res
 * handle}, usually in their constructor, and keep it in a field. The handle looks up the current
 * value on every {@link Res#get()} call, so it stays valid when the value is replaced. Request
 * handles only in constructors, never inside {@code execute()} or in helper methods; helpers get
 * the resolved value as a parameter.
 *
 * <p><b>Writing:</b> Each resource has exactly one owner that inserts, replaces and removes it. For
 * the level, this is the {@link engine.systems.LevelSystem}.
 *
 * <p><b>Core and feature resources:</b> Core resources of the engine have a typed accessor in this
 * class, like {@link #level()}. Resources defined in a feature or room must not be added here,
 * because the engine must not depend on them. Instead, the resource type offers its own accessor,
 * for example {@code public static Res<MyIndex> res() { return Resources.res(MyIndex.class); }}.
 * This keeps the key in one place. Readers must expect that such a resource is missing, for example
 * because its owning system is not registered.
 */
public final class Resources {

  private static final Map<Class<?>, Object> STORE = new ConcurrentHashMap<>();

  private Resources() {}

  /**
   * Returns a handle to the currently loaded level.
   *
   * @return a handle resolving to the current level, or to an empty {@link Optional} if no level is
   *     loaded
   */
  public static Res<ILevel> level() {
    return res(ILevel.class);
  }

  /**
   * Returns a handle to the resource of the given type.
   *
   * <p>The handle can be requested before the resource is inserted. It resolves to the value that
   * is stored at the time of each {@link Res#get()} call.
   *
   * @param type the type the resource is stored under
   * @param <T> type of the resource
   * @return a handle resolving to the current value, or to an empty {@link Optional} if no value is
   *     stored
   */
  public static <T> Res<T> res(final Class<T> type) {
    return () -> Optional.ofNullable(type.cast(STORE.get(type)));
  }

  /**
   * Inserts or replaces the resource of the given type.
   *
   * <p>Only the owner of a resource should call this method. Passing {@code null} removes the
   * resource.
   *
   * @param type the type the resource is stored under
   * @param value the new value, or {@code null} to remove the resource
   * @param <T> type of the resource
   */
  public static <T> void insert(final Class<T> type, final T value) {
    if (value == null) {
      STORE.remove(type);
    } else {
      STORE.put(type, value);
    }
  }

  /**
   * Removes the resource of the given type.
   *
   * <p>Only the owner of a resource should call this method. Existing handles stay valid and
   * resolve to an empty {@link Optional} until a new value is inserted.
   *
   * @param type the type the resource is stored under
   */
  public static void remove(final Class<?> type) {
    STORE.remove(type);
  }

  /**
   * Removes all resources.
   *
   * <p>Intended for tests that need to reset the global state.
   */
  public static void clear() {
    STORE.clear();
  }
}
