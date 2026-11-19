package engine.rendering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import engine.Entity;
import engine.components.DrawComponent;
import engine.components.PositionComponent;
import engine.utils.Point;
import engine.utils.Rectangle;
import engine.utils.components.draw.TextureMap;
import engine.utils.components.draw.animation.Animation;
import engine.utils.components.draw.animation.AnimationConfig;
import engine.utils.components.path.SimpleIPath;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/** Visibility and contact-shadow behaviour without shader or framebuffer creation. */
class AtmosphereRendererTest {
  @Test
  void hiddenAreasExtinguishLightsFlamesAndTrailsAndDistantEntitiesAreCulled()
      throws ReflectiveOperationException {
    DrawComponent draw = mock(DrawComponent.class);
    Animation animation = mock(Animation.class);
    when(draw.isVisible()).thenReturn(true);
    when(draw.tintColor()).thenReturn(-1);
    when(draw.currentAnimation()).thenReturn(animation);
    when(animation.sourcePath()).thenReturn(Optional.of(new SimpleIPath("objects/torch/fire.png")));
    when(draw.getWidth()).thenReturn(1f);
    when(draw.getHeight()).thenReturn(1f);
    Texture texture = mock(Texture.class);
    SpriteBatch batch = mock(SpriteBatch.class);
    Entity entity = mock(Entity.class);
    try (MockedStatic<RenderTextures> helpers = mockStatic(RenderTextures.class)) {
      helpers.when(RenderTextures::pixel).thenReturn(texture);
      helpers.when(RenderTextures::glow).thenReturn(texture);
      AtmosphereRenderer renderer = new AtmosphereRenderer();
      renderer.addEntity(entity, draw, new PositionComponent(new Point(1, 1)), false);
      when(animation.sourcePath()).thenReturn(Optional.of(new SimpleIPath("fireball.png")));
      renderer.addEntity(entity, draw, new PositionComponent(new Point(3, 3)), false);
      renderer.addEntity(entity, draw, new PositionComponent(new Point(100, 100)), false);
      renderer.hide(List.of());
      assertEquals(List.of(new Point(1.5f, 1.6f), new Point(3.5f, 3.6f)), lightPositions(renderer));
      renderer.drawParticles(batch);
      verify(batch, times(2))
          .draw(any(Texture.class), anyFloat(), anyFloat(), anyFloat(), anyFloat());
      clearInvocations(batch);
      renderer.hide(List.of(new Rectangle(2, 2, 0, 0)));
      assertEquals(List.of(new Point(3.5f, 3.6f)), lightPositions(renderer));
      renderer.drawParticles(batch);
      verify(batch, times(1))
          .draw(any(Texture.class), anyFloat(), anyFloat(), anyFloat(), anyFloat());
      clearInvocations(batch);
      renderer.hide(List.of(new Rectangle(2, 2, 2, 2)));
      assertEquals(List.of(), lightPositions(renderer));
      renderer.drawParticles(batch);
      verify(batch, never())
          .draw(any(Texture.class), anyFloat(), anyFloat(), anyFloat(), anyFloat());
      renderer.dispose();
    }
  }

  private static List<Point> lightPositions(AtmosphereRenderer renderer)
      throws ReflectiveOperationException {
    Field field = AtmosphereRenderer.class.getDeclaredField("lights");
    field.setAccessible(true);
    @SuppressWarnings("unchecked")
    List<Light> lights = (List<Light>) field.get(renderer);
    return lights.stream().map(Light::position).toList();
  }

  @Test
  void transparentEntitiesHaveNoContactShadowAndUnknownPixelsUseABoxBase() {
    DrawComponent draw = mock(DrawComponent.class);
    Animation animation = mock(Animation.class);
    when(draw.currentAnimation()).thenReturn(animation);
    when(animation.getConfig()).thenReturn(new AnimationConfig());
    Texture texture = mock(Texture.class);
    when(texture.getWidth()).thenReturn(16);
    when(texture.getHeight()).thenReturn(16);
    Sprite sprite = new Sprite(texture);
    when(draw.getSprite()).thenReturn(sprite);
    when(draw.getWidth()).thenReturn(2f);
    when(draw.getHeight()).thenReturn(3f);
    PositionComponent position = new PositionComponent(new Point(4, 5));
    TextureMap map = mock(TextureMap.class);
    when(map.opaquePixels(texture)).thenReturn(Optional.empty());
    SpriteBatch batch = mock(SpriteBatch.class);
    try (MockedStatic<TextureMap> textures = mockStatic(TextureMap.class);
        MockedStatic<RenderTextures> helpers = mockStatic(RenderTextures.class)) {
      textures.when(TextureMap::instance).thenReturn(map);
      helpers.when(RenderTextures::blobShadow).thenReturn(texture);
      AtmosphereRenderer renderer = new AtmosphereRenderer();
      when(draw.tintColor()).thenReturn(0xffffff00);
      renderer.drawShadow(batch, draw, position);
      verify(batch, never())
          .draw(any(Texture.class), anyFloat(), anyFloat(), anyFloat(), anyFloat());
      when(draw.tintColor()).thenReturn(-1);
      renderer.drawShadow(batch, draw, position);
      // BOX_BASE spans 10%-90% of the sprite, plus four art pixels of padding.
      float width = (0.9f - 0.1f) * 2 + 0.25f;
      verify(batch).draw(texture, 5 - width / 2, 5 - 0.45f * 0.55f, width, 0.45f);
      renderer.baseColor(batch, draw, position, false);
      verify(batch).setColor(0, 0, 0, 1);
      position.position(new Point(0, 0));
      renderer.baseColor(batch, draw, position, true);
      verify(batch).setColor(128 / 255f, 0, 1, 1);
      renderer.endBases(batch);
      verify(batch).enableBlending();
    }
  }
}
