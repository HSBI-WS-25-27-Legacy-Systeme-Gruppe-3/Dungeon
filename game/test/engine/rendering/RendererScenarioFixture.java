package engine.rendering;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import engine.Game;
import engine.level.Tile;
import engine.level.elements.ILevel;
import engine.level.elements.tile.FloorTile;
import engine.level.elements.tile.WallTile;
import engine.level.utils.Coordinate;
import engine.level.utils.DesignLabel;
import engine.systems.CameraSystem;
import engine.utils.components.draw.FrameBufferPool;
import engine.utils.components.draw.TextureMap;
import engine.utils.components.path.IPath;
import engine.utils.settings.ClientSettings;
import feature.systems.LevelEditorSystem;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

/**
 * Isolated asset and graphics boundaries for rendering scenarios; production algorithms stay real.
 */
final class RendererScenarioFixture {
  final OrthographicCamera camera = new OrthographicCamera(8, 6);
  final SpriteBatch batch = mock(SpriteBatch.class);
  final FrameBufferPool pool = mock(FrameBufferPool.class);
  final ILevel level = mock(ILevel.class);
  final LevelRenderer tiles = new LevelRenderer();
  final List<Light> lights = new ArrayList<>();
  final List<Upload> uploads = new ArrayList<>();
  final List<Texture> occlusionTextures = new ArrayList<>();
  final Map<Texture, BitSet> masks = new HashMap<>();
  final List<Color> colors = new ArrayList<>();
  private final Map<String, Texture> textures = new HashMap<>();
  private final Map<String, ShaderProgram> shaders = new HashMap<>();
  private final Map<DesignLabel, DesignTileset> tilesets = new EnumMap<>(DesignLabel.class);
  Tile[][] layout = floors(3, 3);
  Atmosphere atmosphere = new Atmosphere(Color.BLACK, Color.WHITE, Color.WHITE, 0, 0, 0, false);
  boolean shaderCompiles = true;
  boolean decor = true;
  AtmosphereRenderer renderer;
  AmbientParticles particles;

  private RendererScenarioFixture(boolean variants) {
    camera.position.set(4, 3, 0);
    camera.update();
    when(level.layout()).thenAnswer(invocation -> layout);
    when(level.atmosphere()).thenAnswer(invocation -> atmosphere);
    doAnswer(
            invocation -> {
              List<Light> collected = invocation.getArgument(0);
              collected.addAll(lights);
              return null;
            })
        .when(level)
        .lights(any());
    when(pool.obtain(anyInt(), anyInt()))
        .thenAnswer(invocation -> buffer(invocation.getArgument(0), invocation.getArgument(1)));
    doAnswer(
            invocation -> {
              colors.add(new Color((Color) invocation.getArgument(0)));
              return null;
            })
        .when(batch)
        .setColor(any(Color.class));
    for (DesignLabel design : DesignLabel.values()) {
      float[][] insets = new float[48][3];
      int[][] rectangles = new int[48][];
      for (int cell = 0; cell < 48; cell++) {
        insets[cell] = new float[] {2 / 16f, 3 / 16f, 4 / 16f};
        rectangles[cell] = new int[] {0, 12, 16, 4};
      }
      tilesets.put(
          design,
          new DesignTileset(
              TileTextures.wallAtlas(design),
              variants
                  ? Optional.of(TileTextures.path(design, "floor/variants.png"))
                  : Optional.empty(),
              true,
              insets,
              rectangles));
    }
  }

  static void run(boolean variants, boolean reduced, Consumer<RendererScenarioFixture> check) {
    Graphics originalGraphics = Gdx.graphics;
    Files originalFiles = Gdx.files;
    GL20 originalGl = Gdx.gl;
    boolean originalPedantic = ShaderProgram.pedantic;
    RendererScenarioFixture fixture = new RendererScenarioFixture(variants);
    Graphics graphics = mock(Graphics.class);
    when(graphics.getBackBufferScale()).thenReturn(1f);
    Files files = mock(Files.class);
    when(files.internal(anyString()))
        .thenAnswer(
            invocation -> {
              FileHandle file = mock(FileHandle.class);
              when(file.readString()).thenReturn(invocation.getArgument(0));
              return file;
            });
    TextureMap textureMap = mock(TextureMap.class);
    when(textureMap.textureAt(any(IPath.class)))
        .thenAnswer(
            invocation -> fixture.texture(((IPath) invocation.getArgument(0)).pathString()));
    when(textureMap.opaquePixels(any(Texture.class)))
        .thenAnswer(
            invocation -> Optional.ofNullable(fixture.masks.get(invocation.getArgument(0))));
    // The editor creates its shared font before its static methods can be mocked.
    Gdx.gl = mock(GL20.class);
    try (MockedStatic<Game> game = mockStatic(Game.class);
        MockedStatic<CameraSystem> cameras = mockStatic(CameraSystem.class);
        MockedStatic<ClientSettings> settings = mockStatic(ClientSettings.class);
        MockedStatic<LevelEditorSystem> editor = mockStatic(LevelEditorSystem.class);
        MockedStatic<FrameBufferPool> pools = mockStatic(FrameBufferPool.class);
        MockedStatic<TextureMap> maps = mockStatic(TextureMap.class);
        MockedStatic<DesignTileset> sets = mockStatic(DesignTileset.class);
        MockedStatic<RenderTextures> helpers = mockStatic(RenderTextures.class);
        MockedConstruction<AmbientParticles> particles = mockConstruction(AmbientParticles.class);
        MockedConstruction<ShaderProgram> programs =
            mockConstruction(
                ShaderProgram.class,
                (shader, context) -> {
                  when(shader.isCompiled()).thenAnswer(invocation -> fixture.shaderCompiles);
                  when(shader.getLog()).thenReturn("synthetic shader error");
                  fixture.shaders.put((String) context.arguments().get(1), shader);
                });
        MockedConstruction<Texture> generated =
            mockConstruction(
                Texture.class,
                (texture, context) -> {
                  Pixmap pixels = (Pixmap) context.arguments().getFirst();
                  when(texture.getWidth()).thenReturn(pixels.getWidth());
                  when(texture.getHeight()).thenReturn(pixels.getHeight());
                  fixture.occlusionTextures.add(texture);
                  doAnswer(
                          invocation -> {
                            Pixmap uploaded = invocation.getArgument(0);
                            fixture.uploads.add(Upload.copy(texture, uploaded));
                            return null;
                          })
                      .when(texture)
                      .draw(any(Pixmap.class), anyInt(), anyInt());
                })) {
      Gdx.graphics = graphics;
      Gdx.files = files;
      game.when(Game::currentLevel).thenReturn(Optional.of(fixture.level));
      cameras.when(CameraSystem::camera).thenReturn(fixture.camera);
      settings.when(ClientSettings::reduceEffects).thenReturn(reduced);
      pools.when(FrameBufferPool::getInstance).thenReturn(fixture.pool);
      maps.when(TextureMap::instance).thenReturn(textureMap);
      sets.when(() -> DesignTileset.of(any(DesignLabel.class)))
          .thenAnswer(
              invocation -> {
                DesignTileset base = fixture.tilesets.get(invocation.getArgument(0));
                return new DesignTileset(
                    base.walls(),
                    base.floorVariants(),
                    fixture.decor,
                    base.insets(),
                    base.floorRects());
              });
      helpers.when(RenderTextures::pixel).thenAnswer(invocation -> fixture.texture("pixel"));
      helpers.when(RenderTextures::blobShadow).thenAnswer(invocation -> fixture.texture("blob"));
      helpers
          .when(RenderTextures::wallShadowTop)
          .thenAnswer(invocation -> fixture.texture("shadowTop"));
      helpers
          .when(RenderTextures::wallShadowSide)
          .thenAnswer(invocation -> fixture.texture("shadowSide"));
      helpers.when(RenderTextures::abyssFade).thenAnswer(invocation -> fixture.texture("abyss"));
      fixture.renderer = new AtmosphereRenderer();
      fixture.particles = particles.constructed().getFirst();
      try {
        check.accept(fixture);
      } finally {
        fixture.renderer.dispose();
      }
    } finally {
      Gdx.graphics = originalGraphics;
      Gdx.files = originalFiles;
      Gdx.gl = originalGl;
      ShaderProgram.pedantic = originalPedantic;
    }
  }

  void begin(float delta, float time) {
    renderer.beginFrame(800, 600, delta, time);
  }

  void grade() {
    renderer.grade(buffer(800, 600), Optional.empty(), buffer(800, 600), batch);
  }

  Texture texture(String name) {
    return textures.computeIfAbsent(
        name,
        key -> {
          return mock(
              Texture.class,
              org.mockito.Mockito.withSettings()
                  .name(key)
                  .defaultAnswer(
                      invocation -> {
                        if (invocation.getMethod().getName().equals("getWidth")) return 192;
                        if (invocation.getMethod().getName().equals("getHeight")) return 64;
                        return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
                      }));
        });
  }

  ShaderProgram shader(String name) {
    return shaders.get("shaders/atmosphere/" + name);
  }

  static Tile[][] floors(int width, int height) {
    Tile[][] tiles = new Tile[height][width];
    for (int y = 0; y < height; y++)
      for (int x = 0; x < width; x++)
        tiles[y][x] = new FloorTile(new Coordinate(x, y), DesignLabel.DEFAULT);
    return tiles;
  }

  static Tile wall(int x, int y) {
    return new WallTile(new Coordinate(x, y), DesignLabel.DEFAULT);
  }

  private static FrameBuffer buffer(int width, int height) {
    FrameBuffer buffer = mock(FrameBuffer.class);
    when(buffer.getWidth()).thenReturn(width);
    when(buffer.getHeight()).thenReturn(height);
    when(buffer.getColorBufferTexture()).thenReturn(mock(Texture.class));
    return buffer;
  }

  /**
   * Snapshot taken at texture upload, before the renderer reuses or disposes the pixmap.
   *
   * @param texture receiving texture
   * @param width image width in pixels
   * @param height image height in pixels
   * @param pixels RGBA8888 pixels in row order
   */
  record Upload(Texture texture, int width, int height, int[] pixels) {
    static Upload copy(Texture texture, Pixmap source) {
      int[] pixels = new int[source.getWidth() * source.getHeight()];
      for (int y = 0; y < source.getHeight(); y++)
        for (int x = 0; x < source.getWidth(); x++)
          pixels[y * source.getWidth() + x] = source.getPixel(x, y);
      return new Upload(texture, source.getWidth(), source.getHeight(), pixels);
    }

    int pixel(int x, int y) {
      return pixels[y * width + x];
    }
  }
}
