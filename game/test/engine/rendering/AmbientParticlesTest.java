package engine.rendering;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import engine.level.Tile;
import engine.level.elements.tile.FloorTile;
import engine.level.utils.Coordinate;
import engine.level.utils.DesignLabel;
import engine.utils.Point;
import engine.utils.Rectangle;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

/** Particle spawning and expiry observed through their drawing output. */
class AmbientParticlesTest {
  @Test
  void footstepsCreateTwoPuffsThatExpireWithoutFurtherSteps() {
    withTextures(
        (particles, batch) -> {
          particles.step(new Point(2, 3), 1);
          particles.drawPuffs(batch);
          verify(batch, times(2))
              .draw(any(Texture.class), anyFloat(), anyFloat(), eq(0.125f), eq(0.125f));
          // Updates cap a long frame at 0.1 seconds; enough frames still expire every puff.
          for (int i = 0; i < 10; i++)
            particles.update(1, i, new Rectangle(8, 8), List.of(), List.of(), List.of());
          clearInvocations(batch);
          particles.drawPuffs(batch);
          verify(batch, never())
              .draw(any(Texture.class), anyFloat(), anyFloat(), anyFloat(), anyFloat());
        });
  }

  @Test
  void sparksAndMotesRequireTheirSourcesAndOutliveThemBriefly() {
    withTextures(
        (particles, batch) -> {
          Rectangle view = new Rectangle(8, 8);
          Light plain = Light.steady(new Point(2, 2), Color.WHITE, 1);
          Light magic = new Light(new Point(3, 3), Color.BLUE, 4, 1, 0, true);
          particles.update(0.1f, 0, view, List.of(), List.of(), List.of(plain));
          particles.drawMotes(batch, 0);
          particles.drawSparks(batch, List.of(), 0);
          verify(batch, never())
              .draw(any(Texture.class), anyFloat(), anyFloat(), anyFloat(), anyFloat());
          for (int i = 0; i < 4; i++)
            particles.update(
                0.1f,
                i * 0.1f,
                view,
                List.of(new Point(2, 2)),
                List.of(new Point(4, 4)),
                List.of(plain, magic));
          particles.drawMotes(batch, 0.4f);
          verify(batch, times(4))
              .draw(any(Texture.class), anyFloat(), anyFloat(), anyFloat(), anyFloat());
          clearInvocations(batch);
          particles.drawSparks(batch, List.of(), 0.4f);
          verify(batch, atLeastOnce())
              .draw(any(Texture.class), anyFloat(), anyFloat(), anyFloat(), anyFloat());
          clearInvocations(batch);
          particles.drawEmbers(batch, List.of(), 0.4f);
          verify(batch, times(2))
              .draw(any(Texture.class), anyFloat(), anyFloat(), anyFloat(), anyFloat());
          for (int i = 0; i < 40; i++)
            particles.update(0.1f, 1 + i * 0.1f, view, List.of(), List.of(), List.of());
          clearInvocations(batch);
          particles.drawMotes(batch, 5);
          particles.drawSparks(batch, List.of(), 5);
          particles.drawEmbers(batch, List.of(), 5);
          verify(batch, never())
              .draw(any(Texture.class), anyFloat(), anyFloat(), anyFloat(), anyFloat());
        });
  }

  @Test
  void dustIsDrawnOnThePixelGridOverVisibleGroundOnly() {
    withTextures(
        (particles, batch) -> {
          particles.update(
              0, 0, new Rectangle(0, 0, 0.53f, 0.78f), List.of(), List.of(), List.of());
          FloorTile floor = new FloorTile(new Coordinate(0, 0), DesignLabel.DEFAULT);
          particles.drawDust(batch, new Tile[][] {{floor}}, 0);
          ArgumentCaptor<Float> x = ArgumentCaptor.forClass(Float.class);
          ArgumentCaptor<Float> y = ArgumentCaptor.forClass(Float.class);
          verify(batch, times(110))
              .draw(any(Texture.class), x.capture(), y.capture(), anyFloat(), anyFloat());
          assertTrue(x.getAllValues().stream().allMatch(v -> v == 0.5f));
          assertTrue(y.getAllValues().stream().allMatch(v -> v == 0.75f));
          clearInvocations(batch);
          floor.visible(false);
          particles.drawDust(batch, new Tile[][] {{floor}}, 0);
          particles.drawDust(batch, new Tile[0][], 0);
          particles.drawDust(batch, new Tile[][] {{null}}, 0);
          verify(batch, never())
              .draw(any(Texture.class), anyFloat(), anyFloat(), anyFloat(), anyFloat());
          // Moving the viewport relocates dust rather than leaving it behind.
          particles.update(0, 0, new Rectangle(0, 0, 5.2f, 6.2f), List.of(), List.of(), List.of());
          Tile[][] moved = new Tile[7][6];
          moved[6][5] = floor;
          floor.visible(true);
          particles.drawDust(batch, moved, 0);
          verify(batch, times(110))
              .draw(any(Texture.class), eq(5.1875f), eq(6.1875f), anyFloat(), anyFloat());
        });
  }

  /** A particle assertion with isolated helper textures. */
  private interface ParticleCheck {
    void check(AmbientParticles particles, SpriteBatch batch);
  }

  private static void withTextures(ParticleCheck check) {
    Texture texture = mock(Texture.class);
    try (MockedStatic<RenderTextures> textures = mockStatic(RenderTextures.class)) {
      textures.when(RenderTextures::pixel).thenReturn(texture);
      textures.when(RenderTextures::glow).thenReturn(texture);
      check.check(new AmbientParticles(), mock(SpriteBatch.class));
    }
  }
}
