package engine.rendering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import engine.level.Tile;
import engine.level.elements.tile.DoorTile;
import engine.level.elements.tile.ExitTile;
import engine.level.elements.tile.HoleTile;
import engine.level.elements.tile.SkipTile;
import engine.level.utils.Coordinate;
import engine.level.utils.DesignLabel;
import engine.utils.Point;
import engine.utils.Rectangle;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

/** Checks occlusion pixels and light commands at the real renderer's graphics boundary. */
class AtmosphereLightingSceneTest {
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
