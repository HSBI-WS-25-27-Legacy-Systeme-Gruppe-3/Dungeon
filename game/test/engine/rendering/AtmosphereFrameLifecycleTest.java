package engine.rendering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import engine.Entity;
import engine.Game;
import engine.components.DrawComponent;
import engine.components.PositionComponent;
import engine.level.Tile;
import engine.level.elements.ILevel;
import engine.systems.CameraSystem;
import engine.utils.Point;
import engine.utils.Rectangle;
import engine.utils.components.draw.FrameBufferPool;
import engine.utils.components.draw.animation.Animation;
import engine.utils.components.path.SimpleIPath;
import engine.utils.settings.ClientSettings;
import feature.systems.LevelEditorSystem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

/** Frame orchestration contracts without creating an OpenGL context. */
class AtmosphereFrameLifecycleTest {
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void eachFrameReadsTheCurrentAtmosphereAndEditorModeDisablesGrading(boolean editorMode) {
    withRenderer(
        true,
        fixture -> {
          fixture.level = mock(ILevel.class);
          fixture.editor = editorMode;
          when(fixture.level.layout()).thenReturn(new Tile[0][]);
          Atmosphere[] moods = {Atmosphere.DUNGEON, Atmosphere.COLD};
          for (Atmosphere mood : moods) {
            when(fixture.level.atmosphere()).thenReturn(mood);
            fixture.renderer.beginFrame(800, 600, 0.016f, 0);
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
            for (ShaderProgram shader : fixture.shaders.values()) clearInvocations(shader);
          }
          fixture.renderer.dispose();
        });
  }

  @Test
  void beginningAnotherFrameClearsEntityLightsFlamesAndTrails() {
    try (MockedConstruction<AmbientParticles> particles =
        mockConstruction(AmbientParticles.class)) {
      withRenderer(
          false,
          fixture -> {
            fixture.renderer.beginFrame(800, 600, 0.016f, 1);
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
              verify(particles.constructed().getFirst())
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
            clearInvocations(particles.constructed().getFirst());
            fixture.renderer.beginFrame(800, 600, 0.016f, 2);
            try {
              fixture.renderer.drawParticles(fixture.batch);
              verify(particles.constructed().getFirst())
                  .update(
                      eq(0.016f),
                      eq(2f),
                      any(Rectangle.class),
                      eq(List.of()),
                      eq(List.of()),
                      eq(List.of()));
            } finally {
              fixture.renderer.endFrame();
              fixture.renderer.dispose();
            }
          });
    }
  }

  @Test
  void consecutiveFramesRecalculateDimensionsAndRestoreTheCamera() {
    withRenderer(
        false,
        fixture -> {
          OrthographicCamera camera = fixture.camera;
          float x = camera.position.x;
          float y = camera.position.y;
          fixture.renderer.beginFrame(800, 600, 0.016f, 1);
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
          fixture.renderer.beginFrame(500, 200, 0.02f, 2);
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
    withRenderer(
        reduced,
        fixture -> {
          fixture.renderer.beginFrame(800, 600, 0.016f, 3);
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
          fixture.renderer.beginFrame(400, 300, 0.016f, 4);
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
          fixture.renderer.dispose();
          for (ShaderProgram shader : fixture.shaders.values()) verify(shader).dispose();
        });
  }

  @Test
  void endFrameReleasesAllocatedBuffersAndRestoresCameraAfterDrawingFails() {
    withRenderer(
        true,
        fixture -> {
          fixture.renderer.beginFrame(800, 600, 0.016f, 0);
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
          fixture.renderer.dispose();
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

  private static FrameBuffer buffer(int width, int height) {
    FrameBuffer buffer = mock(FrameBuffer.class);
    when(buffer.getWidth()).thenReturn(width);
    when(buffer.getHeight()).thenReturn(height);
    when(buffer.getColorBufferTexture()).thenReturn(mock(Texture.class));
    return buffer;
  }

  private static void withRenderer(boolean reduced, Consumer<Fixture> check) {
    Graphics originalGraphics = Gdx.graphics;
    Files originalFiles = Gdx.files;
    GL20 originalGl = Gdx.gl;
    boolean originalPedantic = ShaderProgram.pedantic;
    Fixture fixture = new Fixture();
    Graphics graphics = mock(Graphics.class);
    when(graphics.getBackBufferScale()).thenReturn(2f);
    when(graphics.getBackBufferWidth()).thenReturn(1600);
    when(graphics.getBackBufferHeight()).thenReturn(1200);
    Files files = mock(Files.class);
    when(files.internal(org.mockito.ArgumentMatchers.anyString()))
        .thenAnswer(
            invocation -> {
              FileHandle file = mock(FileHandle.class);
              when(file.readString()).thenReturn(invocation.getArgument(0));
              return file;
            });
    when(fixture.pool.obtain(anyInt(), anyInt()))
        .thenAnswer(
            invocation -> {
              FrameBuffer buffer = buffer(invocation.getArgument(0), invocation.getArgument(1));
              fixture.buffers.add(buffer);
              return buffer;
            });
    // The editor initializes its shared font when its class is first loaded, before static
    // mocking takes effect. Supply GL calls already for that initialization.
    Gdx.gl = mock(GL20.class);
    try (MockedStatic<Game> game = mockStatic(Game.class);
        MockedStatic<CameraSystem> cameras = mockStatic(CameraSystem.class);
        MockedStatic<ClientSettings> settings = mockStatic(ClientSettings.class);
        MockedStatic<LevelEditorSystem> editor = mockStatic(LevelEditorSystem.class);
        MockedStatic<FrameBufferPool> pools = mockStatic(FrameBufferPool.class);
        MockedConstruction<ShaderProgram> shaders =
            mockConstruction(
                ShaderProgram.class,
                (shader, context) -> {
                  when(shader.isCompiled()).thenReturn(true);
                  fixture.shaders.put((String) context.arguments().get(1), shader);
                })) {
      Gdx.graphics = graphics;
      Gdx.files = files;
      game.when(Game::currentLevel).thenAnswer(invocation -> Optional.ofNullable(fixture.level));
      editor.when(LevelEditorSystem::active).thenAnswer(invocation -> fixture.editor);
      cameras.when(CameraSystem::camera).thenReturn(fixture.camera);
      settings.when(ClientSettings::reduceEffects).thenReturn(reduced);
      settings.when(ClientSettings::brightness).thenReturn(75);
      pools.when(FrameBufferPool::getInstance).thenReturn(fixture.pool);
      fixture.renderer = new AtmosphereRenderer();
      check.accept(fixture);
    } finally {
      Gdx.graphics = originalGraphics;
      Gdx.files = originalFiles;
      Gdx.gl = originalGl;
      ShaderProgram.pedantic = originalPedantic;
    }
  }

  /** Isolated graphics dependencies and resources observed through their public APIs. */
  private static final class Fixture {
    private final OrthographicCamera camera = new OrthographicCamera(8, 6);
    private final FrameBufferPool pool = mock(FrameBufferPool.class);
    private final SpriteBatch batch = mock(SpriteBatch.class);
    private final List<FrameBuffer> buffers = new ArrayList<>();
    private final Map<String, ShaderProgram> shaders = new HashMap<>();
    private AtmosphereRenderer renderer;
    private ILevel level;
    private boolean editor;

    private Fixture() {
      camera.position.set(1.2345f, 3.4567f, 0);
      camera.update();
    }

    private ShaderProgram shader(String fragment) {
      return shaders.get("shaders/atmosphere/" + fragment);
    }
  }
}
