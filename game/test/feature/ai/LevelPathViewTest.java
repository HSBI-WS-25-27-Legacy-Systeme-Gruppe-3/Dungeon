package feature.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.ai.pfa.DefaultGraphPath;
import com.badlogic.gdx.ai.pfa.GraphPath;
import engine.Entity;
import engine.components.PositionComponent;
import engine.level.DungeonLevel;
import engine.level.Tile;
import engine.level.loader.parsers.V2FormatParser;
import engine.level.utils.DesignLabel;
import engine.resources.Res;
import engine.utils.Point;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link LevelPathView}.
 *
 * <p>The tests use a real level with a single row of four floor tiles. The path covers the first
 * three tiles.
 */
public class LevelPathViewTest {

  private final DungeonLevel level =
      new DungeonLevel(V2FormatParser.loadLevelLayout(List.of("FFFF")), DesignLabel.DEFAULT);
  private final LevelPathView levelPathView = new LevelPathView(Res.of(level));

  private GraphPath<Tile> pathOverFirstThreeTiles() {
    DefaultGraphPath<Tile> path = new DefaultGraphPath<>();
    for (int x = 0; x < 3; x++) {
      path.add(level.tileAt(new Point(x + 0.5f, 0.5f)).orElseThrow());
    }
    return path;
  }

  private static Entity entityAt(final float x) {
    Entity entity = new Entity();
    entity.add(new PositionComponent(new Point(x, 0.5f)));
    return entity;
  }

  /** An empty path counts as finished. */
  @Test
  public void emptyPathIsFinished() {
    assertTrue(levelPathView.pathFinished(entityAt(0.5f), new DefaultGraphPath<>()));
  }

  /** An entity on the last tile of the path has finished it. */
  @Test
  public void entityOnLastTileHasFinishedPath() {
    assertTrue(levelPathView.pathFinished(entityAt(2.5f), pathOverFirstThreeTiles()));
  }

  /** An entity on the first tile of the path has not finished it yet. */
  @Test
  public void entityOnFirstTileHasNotFinishedPath() {
    assertFalse(levelPathView.pathFinished(entityAt(0.5f), pathOverFirstThreeTiles()));
  }

  /** An entity on a tile that is not part of the path has left it. */
  @Test
  public void entityBesideThePathHasLeftIt() {
    assertTrue(levelPathView.pathLeft(entityAt(3.5f), pathOverFirstThreeTiles()));
  }

  /** Without a level, an entity is never on the path. */
  @Test
  public void withoutLevelEntityHasLeftPath() {
    LevelPathView withoutLevel = new LevelPathView(Res.empty());
    assertTrue(withoutLevel.pathLeft(entityAt(0.5f), pathOverFirstThreeTiles()));
  }
}
