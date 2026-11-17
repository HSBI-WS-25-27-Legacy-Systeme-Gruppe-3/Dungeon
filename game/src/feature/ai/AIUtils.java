package feature.ai;

import com.badlogic.gdx.ai.pfa.GraphPath;
import engine.Entity;
import engine.components.PositionComponent;
import engine.level.Tile;
import engine.resources.Resources;

/**
 * Static access to the path operations of {@link LevelPathView} on the level of the game.
 *
 * <p>Intended for code that has no constructor of its own to keep a view, like the AI behaviours.
 * Systems create their own {@link LevelPathView} in the constructor instead.
 */
public class AIUtils {

  private static final LevelPathView LEVEL_PATH_VIEW = new LevelPathView(Resources.level());

  /**
   * Sets the velocity of the passed entity so that it takes the next necessary step to get to the
   * end of the path.
   *
   * @param entity Entity moving on the path.
   * @param path Path on which the entity moves.
   */
  public static void followPath(final Entity entity, final GraphPath<Tile> path) {
    LEVEL_PATH_VIEW.followPath(entity, path);
  }

  /**
   * Checks if the entity is either on the end of the path or has left the path.
   *
   * @param entity Entity to be checked.
   * @param path Path which the entity possibly left or has reached the end of.
   * @return true if the entity is on the end of the path or has left the path, otherwise false.
   */
  public static boolean pathFinishedOrLeft(final Entity entity, final GraphPath<Tile> path) {
    return LEVEL_PATH_VIEW.pathFinishedOrLeft(entity, path);
  }

  /**
   * Checks if the entity is on the end of the path.
   *
   * @param entity Entity to be checked.
   * @param path Path on which the entity possible reached the end.
   * @return true if the entity is on the last tile of the path; false if the entity is not at the
   *     end or if no {@link PositionComponent} is present.
   */
  public static boolean pathFinished(final Entity entity, final GraphPath<Tile> path) {
    return LEVEL_PATH_VIEW.pathFinished(entity, path);
  }

  /**
   * Checks if the entity has left the path.
   *
   * @param entity Entity to be checked.
   * @param path Path to be checked.
   * @return true if the entity's current tile is not part of the given path, or if no {@link
   *     PositionComponent} is present; otherwise false.
   */
  public static boolean pathLeft(final Entity entity, final GraphPath<Tile> path) {
    return LEVEL_PATH_VIEW.pathLeft(entity, path);
  }
}
