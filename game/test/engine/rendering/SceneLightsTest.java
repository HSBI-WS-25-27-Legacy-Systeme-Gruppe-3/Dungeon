package engine.rendering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.badlogic.gdx.graphics.Color;
import engine.Entity;
import engine.components.DrawComponent;
import engine.components.PlayerComponent;
import engine.components.PositionComponent;
import engine.utils.Point;
import engine.utils.Vector2;
import engine.utils.components.draw.animation.Animation;
import engine.utils.components.path.SimpleIPath;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;

/** Entity lighting derived from visibility, player state and animation paths. */
class SceneLightsTest {
  private final Entity entity = mock(Entity.class);
  private final DrawComponent draw = mock(DrawComponent.class);
  private final Animation animation = mock(Animation.class);
  private final PositionComponent position = new PositionComponent(new Point(10, 20));
  private final List<Light> lights = new ArrayList<>();
  private final List<Point> flames = new ArrayList<>();
  private final List<Point> trails = new ArrayList<>();

  private void sprite(String path) {
    when(draw.isVisible()).thenReturn(true);
    when(draw.tintColor()).thenReturn(-1);
    when(draw.getWidth()).thenReturn(2f);
    when(draw.getHeight()).thenReturn(4f);
    when(draw.currentAnimation()).thenReturn(animation);
    when(animation.sourcePath()).thenReturn(Optional.of(new SimpleIPath(path)));
    position.scale(Vector2.of(2, 0.5f));
  }

  private boolean collect(float lantern) {
    return SceneLights.collect(entity, draw, position, lantern, lights, flames, trails);
  }

  @ParameterizedTest
  @CsvSource({
    "objects/torch/fire.png,6.5,0.9,1,0",
    "FIREBALL.png,4.5,1.4,0,1",
    "magic_kettle.png,4,1,0,0",
    "cauldron.png,3.5,0.9,0,0",
    "lamp.png,5.5,1,0,0",
    "lava.png,3,0.9,1,0"
  })
  void emissiveSpritesProduceMatchingLightsAndParticleSources(
      String path, float radius, float intensity, int flameCount, int trailCount) {
    sprite(path);
    assertTrue(collect(0));
    assertEquals(1, lights.size());
    Light light = lights.getFirst();
    assertEquals(new Point(12, 21.2f), light.position());
    assertEquals(radius, light.radius());
    assertEquals(intensity, light.intensity());
    assertEquals(flameCount, flames.size());
    assertEquals(trailCount, trails.size());
    if (flameCount > 0) assertEquals(new Point(12, 21.7f), flames.getFirst());
    if (trailCount > 0) assertEquals(new Point(12, 21), trails.getFirst());
  }

  @Test
  void playersCarryOnlyTheirLanternEvenWithAnEmissiveAnimation() {
    sprite("fireball.png");
    when(entity.isPresent(PlayerComponent.class)).thenReturn(true);
    assertFalse(collect(0));
    assertTrue(lights.isEmpty());
    assertFalse(collect(0.7f));
    assertEquals(1, lights.size());
    assertEquals(new Point(12, 20.9f), lights.getFirst().position());
    assertEquals(0.7f, lights.getFirst().intensity());
    assertTrue(trails.isEmpty());
  }

  @Test
  void invisibleTransparentOffAndPathlessSpritesEmitNothing() {
    sprite("lamp.png");
    when(draw.isVisible()).thenReturn(false);
    assertFalse(collect(1));
    when(draw.isVisible()).thenReturn(true);
    when(draw.tintColor()).thenReturn(0xffffff00);
    assertFalse(collect(1));
    when(draw.tintColor()).thenReturn(-1);
    when(draw.currentStateName()).thenReturn("off");
    assertFalse(collect(1));
    when(draw.currentStateName()).thenReturn("idle");
    when(animation.sourcePath()).thenReturn(Optional.empty());
    assertFalse(collect(1));
    assertTrue(lights.isEmpty());
  }

  @Test
  void screensCastSteadyLightInFrontAndUnlitObjectsCastNone() {
    sprite("screen.png");
    try (MockedStatic<EmissiveSprites> screens = mockStatic(EmissiveSprites.class)) {
      screens.when(() -> EmissiveSprites.lightColor(draw)).thenReturn(Optional.of(Color.GREEN));
      assertTrue(collect(0));
      assertEquals(List.of(Light.steady(new Point(12, 19.85f), Color.GREEN, 0.5f)), lights);
      screens.when(() -> EmissiveSprites.lightColor(draw)).thenReturn(Optional.empty());
      assertFalse(collect(0));
      assertEquals(1, lights.size());
    }
  }
}
