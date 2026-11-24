package engine.level.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Clipboard;
import engine.level.DungeonLevel;
import engine.level.elements.ILevel;
import engine.level.utils.DesignLabel;
import engine.level.utils.LevelElement;
import engine.resources.Resources;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for {@link DungeonSaver} class. */
public class DungeonSaverTest {

  @TempDir Path tempDir;

  private Files originalFiles;
  private Application originalApp;
  private Files files;
  private Application app;
  private Clipboard clipboard;
  private DungeonLevel level;

  @BeforeEach
  void setup() {
    originalFiles = Gdx.files;
    originalApp = Gdx.app;

    files = mock(Files.class);

    when(files.local(anyString()))
        .thenAnswer(
            invocation ->
                new FileHandle(tempDir.resolve((String) invocation.getArgument(0)).toFile()));

    clipboard = mock(Clipboard.class);
    app = mock(Application.class);
    when(app.getClipboard()).thenReturn(clipboard);

    Gdx.files = files;
    Gdx.app = app;

    level =
        new DungeonLevel(
            new LevelElement[][] {
              {LevelElement.WALL, LevelElement.WALL, LevelElement.WALL},
              {LevelElement.WALL, LevelElement.FLOOR, LevelElement.WALL},
              {LevelElement.WALL, LevelElement.WALL, LevelElement.WALL},
            },
            DesignLabel.DEFAULT);
  }

  @AfterEach
  void cleanup() {
    Gdx.files = originalFiles;
    Gdx.app = originalApp;
    Resources.remove(ILevel.class);
  }

  private String readSavedFile(String fileName) throws IOException {
    return java.nio.file.Files.readString(tempDir.resolve(fileName));
  }

  @Test
  void saveCurrentDungeonCopiesToClipboardAndWritesFile() throws IOException {
    Resources.insert(ILevel.class, level);
    String expected = LevelParser.serializeLevel(level);

    DungeonSaver.saveCurrentDungeon("saved");

    verify(clipboard).setContents(expected);
    verify(files).local("saved_1.level");
    assertEquals(expected, readSavedFile("saved_1.level"));
  }

  @Test
  void saveCurrentDungeonHappyPathOverwritesFile() throws IOException {
    Resources.insert(ILevel.class, level);
    java.nio.file.Files.writeString(tempDir.resolve("saved_1.level"), "old content");
    String expected = LevelParser.serializeLevel(level);

    DungeonSaver.saveCurrentDungeon("saved.txt", true);
    String content = readSavedFile("saved_1.level");

    verify(clipboard).setContents(expected);
    verify(files).local("saved_1.level");
    assertEquals(expected, content);
    assertFalse(content.contains("old content"));
  }

  @Test
  void saveCurrentDungeonWithoutLevelSavesNothing() {
    Resources.remove(ILevel.class);

    DungeonSaver.saveCurrentDungeon("saved", true);

    verifyNoInteractions(files, app, clipboard);
  }

  @Test
  void saveCurrentDungeonWithoutDungeonLevelSavesNothing() {
    Resources.insert(ILevel.class, mock(ILevel.class));

    DungeonSaver.saveCurrentDungeon("saved", true);

    verifyNoInteractions(files, app, clipboard);
  }

  @Test
  void normalizeLevelFilePathReplacesExtension() {
    assertEquals("levels/room_2.level", DungeonSaver.normalizeLevelFilePath("levels/room_2.txt"));
    assertEquals("levels/room_2.level", DungeonSaver.normalizeLevelFilePath("levels/room_2.level"));
    assertEquals(
        "levels.old/room_2.level", DungeonSaver.normalizeLevelFilePath("levels.old/room_2"));
  }

  @Test
  void normalizeLevelFilePathAppendsSuffixWithoutUnderscore() {
    assertEquals("levels/room_1.level", DungeonSaver.normalizeLevelFilePath("levels/room.txt"));
    assertEquals("levels\\room_1.level", DungeonSaver.normalizeLevelFilePath("levels\\room"));
    assertEquals(
        "my_levels/room_1.level", DungeonSaver.normalizeLevelFilePath("my_levels/room.level"));
  }

  @Test
  void normalizeLevelFilePathWithoutSeparators() {
    assertEquals("room_1.level", DungeonSaver.normalizeLevelFilePath("room"));
    assertEquals("room_1.level", DungeonSaver.normalizeLevelFilePath("room.txt"));
    assertEquals("room_3.level", DungeonSaver.normalizeLevelFilePath("room_3.txt"));
  }
}
