package engine.rendering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import engine.components.DrawComponent;
import engine.utils.components.draw.animation.Animation;
import engine.utils.components.draw.shader.AbstractShader;
import engine.utils.components.draw.shader.OutlineShader;
import engine.utils.components.draw.shader.ShaderList;
import engine.utils.components.path.IPath;
import engine.utils.components.path.SimpleIPath;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

/** Screen glow masks, frame colours and generated-texture lifecycle. */
class EmissiveSpritesTest {
  @Test
  void maskRejectsTransparentDarkAndUnsaturatedPixelsAndAveragesOnlyTheFrame() {
    IPath path = new SimpleIPath("rendering-test/generated-screen");
    Pixmap source = new Pixmap(5, 1, Pixmap.Format.RGBA8888);
    source.setBlending(Pixmap.Blending.None);
    source.drawPixel(0, 0, 0xff0000ff);
    source.drawPixel(1, 0, 0x00ff00ff);
    source.drawPixel(2, 0, 0xffffffff);
    source.drawPixel(3, 0, 0x200000ff);
    source.drawPixel(4, 0, 0x0000ff7f);
    List<int[]> masks = new ArrayList<>();
    try (MockedConstruction<Texture> textures =
        mockConstruction(
            Texture.class,
            (texture, context) -> {
              Pixmap mask = (Pixmap) context.arguments().getFirst();
              int[] row = new int[mask.getWidth()];
              for (int x = 0; x < row.length; x++) row[x] = mask.getPixel(x, 0);
              masks.add(row);
            })) {
      try {
        EmissiveSprites.glow(path, source);
        assertEquals(
            List.of(-1, -1, 255, 255, 0), Arrays.stream(masks.getFirst()).boxed().toList());
        DrawComponent draw = draw(path, sprite());
        Sprite sprite = draw.getSprite();
        sprite.setRegion(0, 0, 2, 1);
        Color color = EmissiveSprites.lightColor(draw).orElseThrow();
        assertEquals(new Color(1, 1, 0, 1), color);
        assertSame(color, EmissiveSprites.lightColor(draw).orElseThrow());
        sprite.setRegion(2, 0, 3, 1);
        assertTrue(EmissiveSprites.lightColor(draw).isEmpty());
        assertSame(textures.constructed().getFirst(), EmissiveSprites.mask(draw).orElseThrow());
        EmissiveSprites.glow(path, source);
        verify(textures.constructed().getFirst()).dispose();
        EmissiveSprites.forget(path);
        verify(textures.constructed().get(1)).dispose();
        EmissiveSprites.forget(path);
      } finally {
        EmissiveSprites.forget(path);
      }
    } finally {
      source.dispose();
    }
  }

  @Test
  void colourChangingShadersSuppressLightWhileOutlinesPreserveIt() {
    IPath path = new SimpleIPath("rendering-test/shader-screen");
    Pixmap source = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
    source.drawPixel(0, 0, 0x0000ffff);
    try (MockedConstruction<Texture> textures = mockConstruction(Texture.class)) {
      try {
        EmissiveSprites.glow(path, source);
        Sprite sprite = sprite();
        sprite.setRegion(0, 0, 1, 1);
        DrawComponent draw = draw(path, sprite);
        ShaderList shaders = draw.shaders();
        when(shaders.getEnabledSorted()).thenReturn(List.of(mock(OutlineShader.class)));
        assertEquals(Color.BLUE, EmissiveSprites.lightColor(draw).orElseThrow());
        when(shaders.getEnabledSorted()).thenReturn(List.of(mock(AbstractShader.class)));
        assertTrue(EmissiveSprites.lightColor(draw).isEmpty());
      } finally {
        EmissiveSprites.forget(path);
      }
    } finally {
      source.dispose();
    }
  }

  @Test
  void ordinaryAndPathlessAnimationsHaveNoGlow() {
    IPath path = new SimpleIPath("rendering-test/stone.png");
    DrawComponent draw = draw(path, sprite());
    try {
      assertTrue(EmissiveSprites.mask(draw).isEmpty());
      assertTrue(EmissiveSprites.lightColor(draw).isEmpty());
      when(draw.currentAnimation().sourcePath()).thenReturn(Optional.empty());
      assertTrue(EmissiveSprites.mask(draw).isEmpty());
    } finally {
      EmissiveSprites.forget(path);
    }
  }

  private static Sprite sprite() {
    Texture texture = mock(Texture.class);
    when(texture.getWidth()).thenReturn(5);
    when(texture.getHeight()).thenReturn(1);
    return new Sprite(texture);
  }

  private static DrawComponent draw(IPath path, Sprite sprite) {
    DrawComponent draw = mock(DrawComponent.class);
    Animation animation = mock(Animation.class);
    ShaderList shaders = mock(ShaderList.class);
    when(draw.currentAnimation()).thenReturn(animation);
    when(animation.sourcePath()).thenReturn(Optional.of(path));
    when(draw.getSprite()).thenReturn(sprite);
    when(draw.shaders()).thenReturn(shaders);
    when(shaders.getEnabledSorted()).thenReturn(List.of());
    return draw;
  }
}
