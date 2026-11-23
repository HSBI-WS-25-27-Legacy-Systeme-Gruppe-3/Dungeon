package engine.systems;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import engine.Entity;
import engine.components.NetworkPositionComponent;
import engine.components.PositionComponent;
import engine.utils.Point;
import feature.systems.PositionSync;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class NetworkPositionSmoothingSystemTest {

  private static final float POSITION_LERP = 0.55f;
  private static final double DELTA = 0.0001;

  private NetworkPositionSmoothingSystem system;

  private Entity entity;
  private PositionComponent position;
  private NetworkPositionComponent networkPosition;

  private MockedStatic<PositionSync> positionSyncMock;

  private Graphics graphics;

  @BeforeEach
  void setUp() {
    entity = mock(Entity.class);
    position = mock(PositionComponent.class);
    networkPosition = mock(NetworkPositionComponent.class);

    system = spy(new NetworkPositionSmoothingSystem());

    when(entity.fetch(PositionComponent.class)).thenReturn(Optional.of(position));

    when(entity.fetch(NetworkPositionComponent.class)).thenReturn(Optional.of(networkPosition));

    doReturn(Stream.of(entity))
        .when(system)
        .filteredEntityStream(PositionComponent.class, NetworkPositionComponent.class);

    graphics = mock(Graphics.class);
    Gdx.graphics = graphics;

    positionSyncMock = mockStatic(PositionSync.class);
  }

  @AfterEach
  void tearDown() {
    positionSyncMock.close();
    Gdx.graphics = null;
  }

  /**
   * Verifies that an entity moves partially toward its target using the default interpolation
   * factor.
   */
  @Test
  void shouldInterpolatePosition() {
    Point current = new Point(0, 0);
    Point target = new Point(1, 1);

    when(graphics.getDeltaTime()).thenReturn(0f);
    when(position.position()).thenReturn(current);
    when(networkPosition.targetPosition()).thenReturn(target);

    system.execute();

    verifyPosition(0.55f, 0.55f);

    positionSyncMock.verify(() -> PositionSync.syncPosition(entity));
  }

  /** Verifies that large position corrections are applied immediately. */
  @Test
  void shouldSnapPositionWhenDistanceIsTooLarge() {
    Point current = new Point(0, 0);
    Point target = new Point(10, 10);

    when(graphics.getDeltaTime()).thenReturn(1f / 60f);
    when(position.position()).thenReturn(current);
    when(networkPosition.targetPosition()).thenReturn(target);

    system.execute();

    verifyPosition(10, 10);
    positionSyncMock.verify(() -> PositionSync.syncPosition(entity));
  }

  /** Verifies that positions within the settling threshold are immediately set to the target. */
  @Test
  void shouldSettlePositionWhenCloseToTarget() {
    Point current = new Point(5, 5);
    Point target = new Point(5.005F, 5.005F);

    when(graphics.getDeltaTime()).thenReturn(1f / 60f);
    when(position.position()).thenReturn(current);
    when(networkPosition.targetPosition()).thenReturn(target);

    system.execute();

    verifyPosition(5.005f, 5.005f);
    positionSyncMock.verify(() -> PositionSync.syncPosition(entity));
  }

  /** Verifies that interpolation uses the frame delta to compensate for different frame rates. */
  @Test
  void shouldScaleInterpolationBasedOnDeltaTime() {
    Point current = new Point(0, 0);
    Point target = new Point(1, 1);

    float deltaTime = 1f / 30f;

    float expectedLerp = (float) (1 - Math.pow(1 - POSITION_LERP, deltaTime / (1f / 60f)));

    when(graphics.getDeltaTime()).thenReturn(deltaTime);
    when(position.position()).thenReturn(current);
    when(networkPosition.targetPosition()).thenReturn(target);

    system.execute();

    verifyPosition(expectedLerp, expectedLerp);
  }

  /** Verifies that zero delta time falls back to the default lerp. */
  @Test
  void shouldUseDefaultLerpWhenDeltaTimeIsZero() {
    Point current = new Point(0, 0);
    Point target = new Point(1, 1);

    when(graphics.getDeltaTime()).thenReturn(0f);
    when(position.position()).thenReturn(current);
    when(networkPosition.targetPosition()).thenReturn(target);

    system.execute();

    verifyPosition(POSITION_LERP, POSITION_LERP);
  }

  /** Verifies that negative delta time falls back to the default lerp. */
  @Test
  void shouldUseDefaultLerpWhenDeltaTimeIsNegative() {
    Point current = new Point(0, 0);
    Point target = new Point(1, 1);

    when(graphics.getDeltaTime()).thenReturn(-0.1f);
    when(position.position()).thenReturn(current);
    when(networkPosition.targetPosition()).thenReturn(target);

    system.execute();

    verifyPosition(POSITION_LERP, POSITION_LERP);
  }

  /** Verifies that delta times above the maximum frame duration are capped. */
  @Test
  void shouldCapMaximumDeltaTime() {
    Point current = new Point(0, 0);
    Point target = new Point(1, 1);

    float cappedDelta = 0.25f;

    float expectedLerp = (float) (1 - Math.pow(1 - POSITION_LERP, cappedDelta / (1f / 60f)));

    when(graphics.getDeltaTime()).thenReturn(1f);
    when(position.position()).thenReturn(current);
    when(networkPosition.targetPosition()).thenReturn(target);

    system.execute();

    verifyPosition(expectedLerp, expectedLerp);
  }

  /**
   * Verifies that the current position changes when interpolation is required, rather than
   * remaining at its original value.
   */
  @Test
  void shouldChangePositionDuringInterpolation() {
    Point current = new Point(0, 0);
    Point target = new Point(1, 1);

    when(graphics.getDeltaTime()).thenReturn(0f);
    when(position.position()).thenReturn(current);
    when(networkPosition.targetPosition()).thenReturn(target);

    system.execute();

    assertNotEquals(current.x(), 0.55, DELTA);
    assertNotEquals(current.y(), 0.55, DELTA);
  }

  /** Verifies that the position synchronization method is invoked for every processed entity. */
  @Test
  void shouldSynchronizePositionAfterUpdating() {
    Point current = new Point(0, 0);
    Point target = new Point(1, 1);

    when(graphics.getDeltaTime()).thenReturn(0f);
    when(position.position()).thenReturn(current);
    when(networkPosition.targetPosition()).thenReturn(target);

    system.execute();

    positionSyncMock.verify(() -> PositionSync.syncPosition(entity));
  }

  private void verifyPosition(float expectedX, float expectedY) {
    org.mockito.ArgumentCaptor<Point> captor = org.mockito.ArgumentCaptor.forClass(Point.class);

    org.mockito.Mockito.verify(position).position(captor.capture());

    Point actual = captor.getValue();

    assertEquals(expectedX, actual.x(), DELTA);
    assertEquals(expectedY, actual.y(), DELTA);
  }
}
