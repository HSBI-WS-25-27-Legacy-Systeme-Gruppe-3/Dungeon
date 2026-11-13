package feature.systems;

import engine.System;
import engine.level.elements.ILevel;
import engine.resources.Changes;
import engine.resources.Res;
import engine.resources.Resources;
import feature.level.ITickable;

/**
 * Ticks the current level.
 *
 * <p>If the current level implements {@link ITickable}, its {@link ITickable#onTick(boolean)}
 * method is called every tick. The first tick after a new level was loaded is reported as the first
 * tick, so the level can set itself up, for example in {@code DungeonLevel#onFirstTick()}.
 *
 * <p>This system is the reference example for reading a resource and reacting to its changes: it
 * reads the level through a {@link Res} handle and detects level changes with its own {@link
 * Changes} tracker. Both are created in the constructor.
 *
 * @see ITickable
 * @see engine.level.DungeonLevel DungeonLevel
 */
public class LevelTickSystem extends System {

  private final Res<ILevel> level;
  private final Changes levelChanges;

  /** Creates a new LevelTickSystem that ticks the level of the game. */
  public LevelTickSystem() {
    this(Resources.level());
  }

   //Intended for tests, which can pass a fixed or mutable handle instead of the game state.
  LevelTickSystem(final Res<ILevel> level) {
    super(AuthoritativeSide.BOTH);
    this.level = level;
    this.levelChanges = Changes.of(level);
  }

  @Override
  public void execute() {
    // Check on every execute, also without a tickable level, so the tracker stays up to date.
    if (level.get().orElse(null) instanceof ITickable tickable) {
      tickable.onTick(levelChanges.changedSinceLastCheck(););
    }
  }

  @Override
  public void stop() {
    // Can't be stopped
  }
}
