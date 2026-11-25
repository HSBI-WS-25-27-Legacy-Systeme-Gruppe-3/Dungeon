package engine.rendering;

import static engine.rendering.RendererScenarioFixture.buffer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import engine.Entity;
import engine.components.DrawComponent;
import engine.components.PositionComponent;
import engine.level.Tile;
import engine.level.elements.tile.DoorTile;
import engine.level.elements.tile.ExitTile;
import engine.level.elements.tile.HoleTile;
import engine.level.elements.tile.SkipTile;
import engine.level.utils.Coordinate;
import engine.level.utils.DesignLabel;
import engine.utils.Point;
import engine.utils.Rectangle;
import engine.utils.Vector2;
import engine.utils.components.draw.TextureMap;
import engine.utils.components.draw.animation.Animation;
import engine.utils.components.draw.animation.AnimationConfig;
import engine.utils.components.path.SimpleIPath;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
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

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void eachFrameReadsTheCurrentAtmosphereAndEditorModeDisablesGrading(boolean editorMode) {
    withRenderer(
        true,
        fixture -> {
          fixture.levelLoaded = true;
          fixture.editorMode = editorMode;
          fixture.layout = new Tile[0][];
          Atmosphere[] moods = {Atmosphere.DUNGEON, Atmosphere.COLD};
          for (Atmosphere mood : moods) {
            fixture.atmosphere = mood;
            fixture.begin(0.016f, 0);
            try {
              FrameBuffer graded =
                  fixture.renderer.grade(
                      buffer(1600, 1200), Optional.empty(), buffer(1600, 1200), fixture.batch);
              verify(fixture.shader("composite.frag"))
                  .setUniformf(
                      "u_ambient",
                      editorMode ? 1f : mood.ambient().r,
                      editorMode ? 1f : mood.ambient().g,
                      editorMode ? 1f : mood.ambient().b);
              fixture.renderer.present(graded, fixture.batch);
              verify(fixture.shader("present.frag"))
                  .setUniformf("u_vignette", editorMode ? 0f : mood.vignette());
            } finally {
              fixture.renderer.endFrame();
            }
            fixture.clearShaderInvocations();
          }
        });
  }

  @Test
  void beginningAnotherFrameClearsEntityLightsFlamesAndTrails() {
    withRenderer(
        false,
        fixture -> {
          fixture.begin(0.016f, 1);
          try {
            DrawComponent draw = mock(DrawComponent.class);
            Animation animation = mock(Animation.class);
            when(draw.isVisible()).thenReturn(true);
            when(draw.tintColor()).thenReturn(-1);
            when(draw.getWidth()).thenReturn(1f);
            when(draw.getHeight()).thenReturn(1f);
            when(draw.currentAnimation()).thenReturn(animation);
            when(animation.sourcePath())
                .thenReturn(Optional.of(new SimpleIPath("objects/torch/fire.png")));
            fixture.renderer.addEntity(
                mock(Entity.class), draw, new PositionComponent(new Point(1, 2)), false);
            when(animation.sourcePath()).thenReturn(Optional.of(new SimpleIPath("fireball.png")));
            fixture.renderer.addEntity(
                mock(Entity.class), draw, new PositionComponent(new Point(2, 3)), false);
            fixture.renderer.drawParticles(fixture.batch);
            verify(fixture.particles)
                .update(
                    eq(0.016f),
                    eq(1f),
                    any(Rectangle.class),
                    argThat(flames -> flames.size() == 1),
                    argThat(trails -> trails.size() == 1),
                    argThat(lights -> lights.size() == 2));
          } finally {
            fixture.renderer.endFrame();
          }
          clearInvocations(fixture.particles);
          fixture.begin(0.016f, 2);
          try {
            fixture.renderer.drawParticles(fixture.batch);
            verify(fixture.particles)
                .update(
                    eq(0.016f),
                    eq(2f),
                    any(Rectangle.class),
                    eq(List.of()),
                    eq(List.of()),
                    eq(List.of()));
          } finally {
            fixture.renderer.endFrame();
          }
        });
  }

  @Test
  void consecutiveFramesRecalculateDimensionsAndRestoreTheCamera() {
    withRenderer(
        false,
        fixture -> {
          OrthographicCamera camera = fixture.camera;
          float x = camera.position.x;
          float y = camera.position.y;
          fixture.begin(0.016f, 1);
          try {
            assertEquals(1600, fixture.renderer.width());
            assertEquals(1200, fixture.renderer.height());
            assertEquals(8, fixture.renderer.view().width());
            assertEquals(6, fixture.renderer.view().height());
            assertNotEquals(x, camera.position.x);
          } finally {
            fixture.renderer.endFrame();
          }
          assertCamera(camera, x, y, 8, 6, 1);

          camera.position.set(-2.1234f, 7.4567f, 0);
          camera.viewportWidth = 10;
          camera.viewportHeight = 4;
          camera.zoom = 0.5f;
          camera.update();
          fixture.begin(500, 200, 0.02f, 2);
          try {
            assertEquals(1000, fixture.renderer.width());
            assertEquals(400, fixture.renderer.height());
            assertEquals(5, fixture.renderer.view().width());
            assertEquals(2, fixture.renderer.view().height());
          } finally {
            fixture.renderer.endFrame();
          }
          assertCamera(camera, -2.1234f, 7.4567f, 10, 4, 0.5f);
        });
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void gradingAndPresentationReturnEveryOwnedBufferAndDisposeShaders(boolean reduced) {
    RendererScenarioFixture disposedFixture =
        withRenderer(
            reduced,
            fixture -> {
              fixture.begin(0.016f, 3);
              FrameBuffer scene = buffer(1600, 1200);
              FrameBuffer bases = buffer(1600, 1200);
              FrameBuffer emissive = buffer(1600, 1200);
              FrameBuffer graded;
              try {
                graded = fixture.renderer.grade(scene, Optional.of(emissive), bases, fixture.batch);
                assertSame(fixture.buffers.getLast(), graded);
                assertEquals(reduced ? 2 : 6, fixture.buffers.size());
                ShaderProgram composite = fixture.shader("composite.frag");
                verify(composite).setUniformf("u_glow", 1f);
                verify(composite).setUniformf("u_bloom", reduced ? 0f : 1f);
                verify(emissive.getColorBufferTexture()).bind(4);
                fixture.renderer.present(graded, fixture.batch);
                verify(fixture.shader("present.frag")).setUniformf("u_grain", reduced ? 0f : 1f);
                verify(fixture.shader("present.frag")).setUniformf("u_brightness", 0.75f);
                verify(graded).begin();
                verify(graded).end();
              } finally {
                fixture.renderer.endFrame();
              }
              for (FrameBuffer owned : fixture.buffers) {
                verify(fixture.pool).free(owned);
                verify(owned.getColorBufferTexture())
                    .setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
                verify(owned, never()).dispose();
              }
              verify(fixture.pool, never()).free(scene);
              verify(fixture.pool, never()).free(bases);
              verify(fixture.pool, never()).free(emissive);
              fixture.renderer.endFrame();
              for (FrameBuffer owned : fixture.buffers) verify(fixture.pool, times(1)).free(owned);

              // A second frame owns a fresh set of buffers and disables absent emissive art.
              int previousCount = fixture.buffers.size();
              fixture.begin(400, 300, 0.016f, 4);
              try {
                FrameBuffer next =
                    fixture.renderer.grade(scene, Optional.empty(), bases, fixture.batch);
                assertEquals(800, next.getWidth());
                assertEquals(600, next.getHeight());
                verify(fixture.shader("composite.frag")).setUniformf("u_glow", 0f);
              } finally {
                fixture.renderer.endFrame();
              }
              assertEquals(previousCount * 2, fixture.buffers.size());
              for (FrameBuffer owned : fixture.buffers) verify(fixture.pool, times(1)).free(owned);
              assertCamera(fixture.camera, 1.2345f, 3.4567f, 8, 6, 1);
            });
    disposedFixture.verifyShadersDisposed();
  }

  @Test
  void endFrameReleasesAllocatedBuffersAndRestoresCameraAfterDrawingFails() {
    withRenderer(
        true,
        fixture -> {
          fixture.begin(0.016f, 0);
          doThrow(new IllegalStateException("drawing failed")).when(fixture.batch).begin();
          try {
            assertThrows(
                IllegalStateException.class,
                () ->
                    fixture.renderer.grade(
                        buffer(1600, 1200), Optional.empty(), buffer(1600, 1200), fixture.batch));
          } finally {
            fixture.renderer.endFrame();
          }
          assertEquals(2, fixture.buffers.size());
          for (FrameBuffer owned : fixture.buffers) verify(fixture.pool).free(owned);
          assertCamera(fixture.camera, 1.2345f, 3.4567f, 8, 6, 1);
        });
  }

  private static void assertCamera(
      OrthographicCamera camera, float x, float y, float width, float height, float zoom) {
    assertEquals(x, camera.position.x);
    assertEquals(y, camera.position.y);
    assertEquals(width, camera.viewportWidth);
    assertEquals(height, camera.viewportHeight);
    assertEquals(zoom, camera.zoom);
  }

  private static RendererScenarioFixture withRenderer(
      boolean reduced, Consumer<RendererScenarioFixture> check) {
    return RendererScenarioFixture.run(
        false,
        reduced,
        fixture -> {
          fixture.levelLoaded = false;
          fixture.camera.position.set(1.2345f, 3.4567f, 0);
          fixture.camera.update();
          fixture.brightness = 75;
          when(fixture.graphics.getBackBufferScale()).thenReturn(2f);
          when(fixture.graphics.getBackBufferWidth()).thenReturn(1600);
          when(fixture.graphics.getBackBufferHeight()).thenReturn(1200);
          check.accept(fixture);
        });
  }

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

  @Test
  void occlusionDistinguishesGroundVoidWallsAndDoorStatesAndUpdatesInPlace() {
    RendererScenarioFixture.run(
        true,
        true,
        fixture -> {
          fixture.layout = RendererScenarioFixture.floors(10, 1);
          fixture.layout[0][1] = RendererScenarioFixture.wall(1, 0);
          fixture.layout[0][2] = null;
          fixture.layout[0][3] = new SkipTile(new Coordinate(3, 0), DesignLabel.DEFAULT);
          fixture.layout[0][4] = new HoleTile(new Coordinate(4, 0), DesignLabel.DEFAULT);
          fixture.layout[0][5] = new ExitTile(new Coordinate(5, 0), DesignLabel.DEFAULT);
          for (int x = 6; x < 10; x++) {
            DoorTile door = new DoorTile(new Coordinate(x, 0), DesignLabel.DEFAULT);
            door.hidden(x >= 8);
            if (x == 7 || x == 8) door.close();
            fixture.layout[0][x] = door;
          }
          fixture.begin(0.1f, 0);
          try {
            fixture.grade();
          } finally {
            fixture.renderer.endFrame();
          }
          RendererScenarioFixture.Upload first = fixture.uploads.getFirst();
          assertEquals(10, first.width());
          assertEquals(1, first.height());
          int[] expected = {
            0x00ff0000,
            0xff203040,
            0xff000000,
            0,
            0x00ff0000,
            0x00ff0000,
            0x00ff0000,
            0xff000000,
            0xff203040,
            0x00ff0000
          };
          for (int x = 0; x < expected.length; x++)
            assertEquals(expected[x], first.pixel(x, 0), "tile " + x);
          verify(first.texture())
              .setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);

          ((DoorTile) fixture.layout[0][7]).open();
          ((DoorTile) fixture.layout[0][8]).open();
          fixture.begin(0.1f, 1);
          try {
            fixture.grade();
          } finally {
            fixture.renderer.endFrame();
          }
          RendererScenarioFixture.Upload second = fixture.uploads.getLast();
          assertSame(first.texture(), second.texture());
          assertEquals(1, fixture.occlusionTextures.size());
          assertEquals(0x00ff0000, second.pixel(7, 0));
          assertEquals(0x00ff0000, second.pixel(8, 0));
          assertEquals(
              0xff000000, first.pixel(7, 0), "the first upload is an independent snapshot");
        });
  }

  @Test
  void changingMapSizeReplacesTheOcclusionTextureAndDisposesBothGenerations() {
    List<Texture> generated = new ArrayList<>();
    RendererScenarioFixture.run(
        true,
        true,
        fixture -> {
          fixture.layout = RendererScenarioFixture.floors(2, 2);
          fixture.begin(0.1f, 0);
          try {
            fixture.grade();
          } finally {
            fixture.renderer.endFrame();
          }
          fixture.layout = RendererScenarioFixture.floors(3, 4);
          fixture.begin(0.1f, 1);
          try {
            fixture.grade();
          } finally {
            fixture.renderer.endFrame();
          }
          assertEquals(2, fixture.occlusionTextures.size());
          assertEquals(3, fixture.uploads.getLast().width());
          assertEquals(4, fixture.uploads.getLast().height());
          fixture.layout = RendererScenarioFixture.floors(3, 2);
          fixture.begin(0.1f, 2);
          try {
            fixture.grade();
          } finally {
            fixture.renderer.endFrame();
          }
          assertEquals(3, fixture.occlusionTextures.size());
          assertEquals(2, fixture.uploads.getLast().height());
          verify(fixture.occlusionTextures.getFirst()).dispose();
          generated.addAll(fixture.occlusionTextures);
        });
    for (Texture texture : generated) verify(texture, times(1)).dispose();
  }

  @ParameterizedTest
  @ValueSource(strings = {"empty", "emptyRow", "outsideX", "outsideY"})
  void emptyOrDistantLayoutsDoNotAllocateOcclusionOrDrawTiles(String scenario) {
    RendererScenarioFixture.run(
        true,
        true,
        fixture -> {
          switch (scenario) {
            case "empty" -> fixture.layout = new Tile[0][];
            case "emptyRow" -> fixture.layout = new Tile[][] {new Tile[0]};
            case "outsideX" -> fixture.camera.position.x = 1000;
            case "outsideY" -> fixture.camera.position.y = 1000;
            default -> throw new AssertionError(scenario);
          }
          fixture.camera.update();
          fixture.lights.add(Light.steady(new Point(2, 3), Color.WHITE, 1));
          fixture.begin(0.1f, 0);
          try {
            fixture.renderer.drawFloor(fixture.batch);
            fixture.renderer.drawWalls(fixture.batch, 0);
            verify(fixture.batch, never())
                .draw(any(Texture.class), anyFloat(), anyFloat(), anyFloat(), anyFloat());
            fixture.grade();
            assertTrue(fixture.uploads.isEmpty());
          } finally {
            fixture.renderer.endFrame();
          }
        });
  }

  @ParameterizedTest
  @CsvSource({"0,0.8", "0.5,0.8", "0.75,0.52", "1,0.24"})
  void ambientBrightnessScalesPointLightWithoutChangingItsPositionOrReach(
      float ambient, float expectedRed) {
    RendererScenarioFixture.run(
        true,
        false,
        fixture -> {
          fixture.atmosphere =
              fixture.atmosphere.withAmbient(new Color(ambient, ambient, ambient, 1));
          fixture.lights.add(new Light(new Point(2, 3), new Color(1, 0.5f, 0.25f, 1), 2, 0.8f, 0));
          fixture.begin(0.1f, 0);
          try {
            fixture.grade();
            ShaderProgram shader = fixture.shader("light.frag");
            verify(shader).setUniformf("u_lightPos", 2f, 3f);
            verify(shader).setUniformf("u_radius", 2f);
            ArgumentCaptor<Float> red = ArgumentCaptor.forClass(Float.class);
            ArgumentCaptor<Float> green = ArgumentCaptor.forClass(Float.class);
            ArgumentCaptor<Float> blue = ArgumentCaptor.forClass(Float.class);
            verify(shader)
                .setUniformf(eq("u_color"), red.capture(), green.capture(), blue.capture());
            assertEquals(expectedRed, red.getValue(), 0.000001f);
            assertEquals(expectedRed / 2, green.getValue(), 0.000001f);
            assertEquals(expectedRed / 4, blue.getValue(), 0.000001f);
            verify(fixture.batch).draw(fixture.texture("pixel"), 0, 1, 4, 8);
          } finally {
            fixture.renderer.endFrame();
          }
        });
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void fireVariesIntensityAtFixedReachAndReducedEffectsDisableTheVariation(boolean reduced) {
    RendererScenarioFixture.run(
        true,
        reduced,
        fixture -> {
          fixture.lights.add(new Light(new Point(2, 3), Color.WHITE, 2, 0.8f, 0.5f));
          for (float time : new float[] {0, 0.25f}) {
            fixture.begin(0.1f, time);
            try {
              fixture.grade();
            } finally {
              fixture.renderer.endFrame();
            }
          }
          ShaderProgram shader = fixture.shader("light.frag");
          verify(shader, times(2)).setUniformf("u_radius", 2f);
          ArgumentCaptor<Float> intensity = ArgumentCaptor.forClass(Float.class);
          verify(shader, times(2))
              .setUniformf(eq("u_color"), intensity.capture(), anyFloat(), anyFloat());
          List<Float> values = intensity.getAllValues();
          if (reduced) {
            assertEquals(List.of(0.8f, 0.8f), values);
          } else {
            assertNotEquals(values.get(0), values.get(1));
            assertTrue(values.stream().allMatch(value -> value >= 0.4f && value <= 1.2f));
          }
        });
  }

  @ParameterizedTest
  @CsvSource({
    "-3,3,false",
    "-2,3,true",
    "2,-7,false",
    "2,-6,true",
    "11,3,false",
    "10,3,true",
    "2,9,false",
    "2,8,true"
  })
  void onlyLightsWhoseReachIntersectsTheViewAreDrawn(float x, float y, boolean drawn) {
    RendererScenarioFixture.run(
        true,
        true,
        fixture -> {
          fixture.lights.add(new Light(new Point(x, y), Color.WHITE, 2, 1, 0));
          fixture.begin(0.1f, 0);
          try {
            fixture.grade();
            verify(fixture.shader("light.frag"), drawn ? times(1) : never())
                .setUniformf("u_lightPos", x, y);
            Texture pixel = fixture.texture("pixel");
            verify(fixture.batch, drawn ? times(1) : never()).draw(pixel, x - 2, y - 2, 4, 8);
          } finally {
            fixture.renderer.endFrame();
          }
        });
  }

  @Test
  void levelPassesAndWallTorchesReachTheFrameRenderer() {
    RendererScenarioFixture.run(
        false,
        false,
        fixture -> {
          fixture.layout = RendererScenarioFixture.floors(8, 4);
          for (int x = 0; x < 8; x++) fixture.layout[2][x] = RendererScenarioFixture.wall(x, 2);
          fixture.atmosphere = Atmosphere.DUNGEON;
          fixture.begin(0.1f, 0);
          try {
            fixture.renderer.drawFloor(fixture.batch);
            verify(fixture.batch)
                .draw(fixture.texture("dungeon/default/floor/floor_1.png"), 0, 0, 1, 1);
            fixture.renderer.drawWalls(fixture.batch, 0);
            verify(fixture.batch)
                .draw(
                    eq(fixture.texture("dungeon/default/wall/walls.png")),
                    eq(0f),
                    eq(2f),
                    eq(1f),
                    eq(1f),
                    org.mockito.ArgumentMatchers.anyInt(),
                    org.mockito.ArgumentMatchers.anyInt(),
                    eq(16),
                    eq(16),
                    eq(false),
                    eq(false));
            fixture.grade();
            verify(fixture.shader("light.frag")).setUniformf("u_lightPos", 0.5f, 1.85f);
            fixture.renderer.drawParticles(fixture.batch);
            verify(fixture.particles)
                .update(
                    eq(0.1f),
                    eq(0f),
                    any(Rectangle.class),
                    org.mockito.ArgumentMatchers.argThat(
                        flames -> flames.contains(new Point(0.5f, 3.0625f))),
                    eq(List.of()),
                    org.mockito.ArgumentMatchers.argThat(lights -> !lights.isEmpty()));
            clearInvocations(fixture.batch);
            fixture.renderer.beginBases(fixture.batch);
            verify(fixture.batch).setShader(fixture.shader("base.frag"));
            fixture.renderer.endBases(fixture.batch);
            verify(fixture.batch).enableBlending();
          } finally {
            fixture.renderer.endFrame();
          }
        });
  }

  @Test
  void failedShaderCompilationIncludesTheAssetAndCompilerLog() {
    RendererScenarioFixture.run(
        false,
        true,
        fixture -> {
          fixture.shaderCompiles = false;
          fixture.begin(0.1f, 0);
          try {
            IllegalStateException error =
                assertThrows(
                    IllegalStateException.class, () -> fixture.renderer.beginBases(fixture.batch));
            assertTrue(error.getMessage().contains("light.frag"));
            assertTrue(error.getMessage().contains("synthetic shader error"));
          } finally {
            fixture.renderer.endFrame();
          }
        });
  }
}
