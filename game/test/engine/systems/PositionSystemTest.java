package engine.systems;

import static org.junit.jupiter.api.Assertions.assertEquals;

import engine.Entity;
import engine.Game;
import engine.components.PositionComponent;
import engine.utils.Point;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import testingUtils.TestLevels;

/** Tests for the {@link PositionSystem} class. */
public class PositionSystemTest {

  private PositionSystem system;
  private PositionComponent pc;

  /** Setup before each test. */
  @BeforeEach
  public void setup() {
    // the only floor tile is at (0, 0), so it is the only free position
    TestLevels.fromAscii("WW", "FW");
    pc = new PositionComponent();
    system = new PositionSystem();
    Game.add(system);
    Entity entity = new Entity();
    Game.add(entity);
    entity.add(pc);
  }

  /** Cleanup after each test. */
  @AfterEach
  public void cleanup() {
    TestLevels.unload();
    Game.removeAllSystems();
    Game.removeAllEntities();
  }

  /** Test that the position is set to a free tile if the position is illegal. */
  @Test
  public void test_illegalPosition() {
    pc.position(PositionComponent.ILLEGAL_POSITION);
    system.execute();
    assertEquals(new Point(0, 0), pc.position());
  }

  /** Test that the position is not changed if the position is legal. */
  @Test
  public void test_legalPosition() {
    pc.position(new Point(2, 2));
    system.execute();
    assertEquals(new Point(2, 2), pc.position());
  }
}
