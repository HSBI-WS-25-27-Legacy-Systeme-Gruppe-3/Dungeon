package engine.rendering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

/** Checks generated pixels before upload, without requiring an OpenGL context. */
class RenderTexturesTest {
  @Test
  void helperTexturesHaveTheirIntendedShapeFalloffAndFilteringAndAreReused() throws Exception {
    Map<Field, Object> saved = new LinkedHashMap<>();
    for (Field field : RenderTextures.class.getDeclaredFields()) {
      if (field.getType() != Texture.class) continue;
      field.setAccessible(true);
      saved.put(field, field.get(null));
      field.set(null, null);
    }
    List<Pixmap> pixels = new ArrayList<>();
    try (MockedConstruction<Texture> textures =
        mockConstruction(
            Texture.class,
            (texture, context) -> {
              Pixmap source = (Pixmap) context.arguments().getFirst();
              Pixmap copy =
                  new Pixmap(source.getWidth(), source.getHeight(), Pixmap.Format.RGBA8888);
              copy.setBlending(Pixmap.Blending.None);
              copy.drawPixmap(source, 0, 0);
              pixels.add(copy);
            })) {
      Texture pixel = WorldOverlay.pixel();
      assertSame(pixel, WorldOverlay.pixel());
      assertEquals(1, pixels.get(0).getWidth());
      assertEquals(-1, pixels.get(0).getPixel(0, 0));
      Texture blob = RenderTextures.blobShadow();
      assertSame(blob, RenderTextures.blobShadow());
      Pixmap shadow = pixels.get(1);
      assertEquals(24, shadow.getWidth());
      assertEquals(10, shadow.getHeight());
      assertEquals(0, shadow.getPixel(0, 0));
      assertTrue(alpha(shadow, 12, 5) > alpha(shadow, 2, 5));
      assertEquals(alpha(shadow, 0, 5), alpha(shadow, 23, 5));
      Texture top = RenderTextures.wallShadowTop();
      assertSame(top, RenderTextures.wallShadowTop());
      assertTrue(alpha(pixels.get(2), 0, 0) > alpha(pixels.get(2), 0, 5));
      assertEquals(0, alpha(pixels.get(2), 0, 6));
      Texture side = RenderTextures.wallShadowSide();
      assertSame(side, RenderTextures.wallShadowSide());
      assertTrue(alpha(pixels.get(3), 0, 0) > alpha(pixels.get(3), 3, 0));
      assertEquals(0, alpha(pixels.get(3), 4, 0));
      Texture glow = RenderTextures.glow();
      assertSame(glow, RenderTextures.glow());
      Pixmap halo = pixels.get(4);
      assertEquals(32, halo.getWidth());
      assertEquals(0, halo.getPixel(0, 0));
      assertTrue(alpha(halo, 16, 16) > alpha(halo, 16, 4));
      for (int y = 0; y < 32; y++)
        for (int x = 0; x < 32; x++) {
          int rgba = halo.getPixel(x, y);
          assertEquals(rgba & 255, rgba >>> 24, "glow must use premultiplied white");
        }
      verify(glow).setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
      Texture abyss = RenderTextures.abyssFade();
      assertSame(abyss, RenderTextures.abyssFade());
      assertTrue(alpha(pixels.get(5), 0, 0) < alpha(pixels.get(5), 0, 15));
      assertEquals(255, alpha(pixels.get(5), 0, 15));
      assertEquals(6, textures.constructed().size());
      for (Texture texture : textures.constructed())
        verify(texture).setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
    } finally {
      for (Pixmap pixmap : pixels) pixmap.dispose();
      for (Map.Entry<Field, Object> entry : saved.entrySet())
        entry.getKey().set(null, entry.getValue());
    }
  }

  private static int alpha(Pixmap pixels, int x, int y) {
    return pixels.getPixel(x, y) & 255;
  }
}
