package engine.level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import engine.level.utils.Coordinate;
import engine.utils.Point;
import engine.utils.Vector2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for the {@link Coordinate} class. */
public class CoordinateTest {

  private final int x = 3;
  private final int y = -3;
  private Coordinate coordinate;

  /** Creates the coordinate used in the tests. */
  @BeforeEach
  public void setup() {
    coordinate = new Coordinate(x, y);
  }

  /** Tests equality of coordinates. */
  @Test
  public void test_equals() {
    assertEquals(coordinate, new Coordinate(x, y));
    assertNotEquals(coordinate, new Coordinate(y, x));
  }

  /** Tests conversion to a point. */
  @Test
  public void test_toPoint() {
    Point point = coordinate.toPoint();
    assertEquals((float) coordinate.x(), point.x(), 0.0f);
    assertEquals((float) coordinate.y(), point.y(), 0.0f);
  }

  /** Tests translation by an integer vector. */
  @Test
  public void testTranslateWithIntegerVector() {
    assertEquals(new Coordinate(5, 4), new Coordinate(3, 3).translate(Vector2.of(2, 1)));
  }

  /** Tests translation with positive fractional values. */
  @Test
  public void testTranslateWithPositiveFractions() {
    assertEquals(new Coordinate(4, 5), new Coordinate(3, 3).translate(Vector2.of(1.8f, 2.9f)));
  }

  /** Tests truncation toward zero for negative values. */
  @Test
  public void testTranslateWithNegativeFractions() {
    assertEquals(new Coordinate(2, 1), new Coordinate(3, 3).translate(Vector2.of(-1.8f, -2.9f)));
  }

  /** Tests fractional offsets smaller than one tile. */
  @Test
  public void testTranslateWithSmallFractions() {
    assertEquals(new Coordinate(3, 3), new Coordinate(3, 3).translate(Vector2.of(0.9f, -0.9f)));
  }

  /** Tests translation by a zero vector. */
  @Test
  public void testTranslateWithZeroVector() {
    assertEquals(new Coordinate(3, 3), new Coordinate(3, 3).translate(Vector2.of(0, 0)));
  }
}
