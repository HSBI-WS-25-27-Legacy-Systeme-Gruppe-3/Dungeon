package engine.rendering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import engine.Entity;
import engine.components.DrawComponent;
import engine.components.PositionComponent;
import engine.utils.Point;
import engine.utils.Rectangle;
import engine.utils.Vector2;
import engine.utils.components.draw.animation.Animation;
import engine.utils.components.draw.animation.AnimationConfig;
import engine.utils.components.path.SimpleIPath;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

/** Entity rendering across frames, including movement, visibility and actual sprite footprints. */
class AtmosphereEntitySceneTest {
  @ParameterizedTest
  @CsvSource({
    "0.5,0.1,true,true,false,false,true",
    "0,0.1,true,true,false,false,false",
    "0.05,0.1,true,true,false,false,false",
    "0.08,0.1,true,true,false,false,false",
    "1.2,0.1,true,true,false,false,false",
    "1.3,0.1,true,true,false,false,false",
    "0.5,0,true,true,false,false,false",
    "0.5,0.1,false,true,false,false,false",
    "0.5,0.1,true,false,false,false,false",
    "0.5,0.1,true,true,true,false,false",
    "0.5,0.1,true,true,false,true,false"
  })
  void footstepsRequireVisibleGroundedWalkingAtTheExpectedSpeed(
      float movement,
      float delta,
      boolean visible,
      boolean grounded,
      boolean glowing,
      boolean reduced,
      boolean puff) {
    RendererScenarioFixture.run(
        false,
        reduced,
        fixture -> {
          Entity entity = mock(Entity.class);
          DrawComponent draw = draw();
          when(draw.isVisible()).thenReturn(visible);
          Animation animation = draw.currentAnimation();
          if (glowing)
            when(animation.sourcePath())
                .thenReturn(Optional.of(new SimpleIPath("objects/torch/fire.png")));
          PositionComponent position = new PositionComponent(new Point(0, 2));
          position.scale(Vector2.of(2, 1));
          fixture.begin(0.1f, 0);
          try {
            fixture.renderer.addEntity(entity, draw, position, grounded);
            verify(fixture.particles, never()).step(any(Point.class), anyFloat());
          } finally {
            fixture.renderer.endFrame();
          }
          clearInvocations(fixture.particles);
          position.position(new Point(movement, 2));
          fixture.begin(delta, 1);
          try {
            fixture.renderer.addEntity(entity, draw, position, grounded);
            if (puff) verify(fixture.particles).step(new Point(movement + 2, 2.0625f), delta);
            else verify(fixture.particles, never()).step(any(Point.class), anyFloat());
          } finally {
            fixture.renderer.endFrame();
          }
        });
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void transparentEntitiesLeaveNoFootstepsAndReducedEffectsSkipParticlePasses(boolean reduced) {
    RendererScenarioFixture.run(
        false,
        reduced,
        fixture -> {
          DrawComponent draw = draw();
          Entity entity = mock(Entity.class);
          PositionComponent position = new PositionComponent(new Point(0, 2));
          fixture.begin(0.1f, 0);
          try {
            fixture.renderer.addEntity(entity, draw, position, true);
          } finally {
            fixture.renderer.endFrame();
          }
          position.position(new Point(0.5f, 2));
          when(draw.tintColor()).thenReturn(0xffffff00);
          fixture.begin(0.1f, 1);
          try {
            fixture.renderer.addEntity(entity, draw, position, true);
            verify(fixture.particles, never()).step(any(Point.class), anyFloat());
            fixture.renderer.drawGroundParticles(fixture.batch);
            fixture.renderer.drawParticles(fixture.batch);
            verify(fixture.particles, reduced ? never() : times(1)).drawPuffs(fixture.batch);
            verify(fixture.particles, reduced ? never() : times(1))
                .update(
                    eq(0.1f),
                    eq(1f),
                    any(Rectangle.class),
                    eq(List.of()),
                    eq(List.of()),
                    eq(List.of()));
          } finally {
            fixture.renderer.endFrame();
          }
        });
  }

  @ParameterizedTest
  @CsvSource({
    "-13,2,false",
    "-12,2,true",
    "2,-13,false",
    "2,-12,true",
    "21,2,false",
    "20,2,true",
    "2,19,false",
    "2,18,true"
  })
  void entitySourcesOutsideTheViewMarginAreIgnored(float x, float y, boolean collected) {
    RendererScenarioFixture.run(
        false,
        false,
        fixture -> {
          DrawComponent draw = draw();
          when(draw.currentAnimation().sourcePath())
              .thenReturn(Optional.of(new SimpleIPath("objects/torch/fire.png")));
          fixture.begin(0.1f, 0);
          try {
            fixture.renderer.addEntity(
                mock(Entity.class), draw, new PositionComponent(new Point(x, y)), false);
            fixture.renderer.drawParticles(fixture.batch);
            verify(fixture.particles)
                .update(
                    eq(0.1f),
                    eq(0f),
                    any(Rectangle.class),
                    argThat(flames -> flames.size() == (collected ? 1 : 0)),
                    eq(List.of()),
                    argThat(lights -> lights.size() == (collected ? 1 : 0)));
          } finally {
            fixture.renderer.endFrame();
          }
        });
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void contactShadowTracksOpaqueFeetAndAnimationMirroring(boolean mirrored) {
    RendererScenarioFixture.run(
        false,
        true,
        fixture -> {
          DrawComponent draw = draw();
          Texture texture = fixture.texture("sprite");
          Sprite sprite = new Sprite(texture);
          sprite.setRegion(0, 0, 8, 8);
          when(draw.getSprite()).thenReturn(sprite);
          when(draw.currentAnimation().getConfig())
              .thenReturn(new AnimationConfig().mirrored(mirrored));
          BitSet opaque = new BitSet();
          opaque.set(5 * 192 + 1);
          fixture.masks.put(texture, opaque);
          PositionComponent position = new PositionComponent(new Point(2, 3));
          fixture.renderer.drawShadow(fixture.batch, draw, position);
          ArgumentCaptor<Float> x = ArgumentCaptor.forClass(Float.class);
          ArgumentCaptor<Float> y = ArgumentCaptor.forClass(Float.class);
          ArgumentCaptor<Float> height = ArgumentCaptor.forClass(Float.class);
          verify(fixture.batch)
              .draw(
                  eq(fixture.texture("blob")),
                  x.capture(),
                  y.capture(),
                  eq(0.5f),
                  height.capture());
          assertEquals(mirrored ? 3.375f : 2.125f, x.getValue(), 0.000001f);
          assertEquals(0.5f * 10 / 24, height.getValue(), 0.000001f);
          assertEquals(3.75f - height.getValue() * 0.55f, y.getValue(), 0.000001f);
        });
  }

  private static DrawComponent draw() {
    DrawComponent draw = mock(DrawComponent.class);
    Animation animation = mock(Animation.class);
    when(draw.currentAnimation()).thenReturn(animation);
    when(animation.sourcePath()).thenReturn(Optional.empty());
    when(draw.isVisible()).thenReturn(true);
    when(draw.tintColor()).thenReturn(-1);
    when(draw.getWidth()).thenReturn(2f);
    when(draw.getHeight()).thenReturn(3f);
    return draw;
  }
}
