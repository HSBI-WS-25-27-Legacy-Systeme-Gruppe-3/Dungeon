package engine.level.loader.parsers;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import engine.level.DungeonLevel;
import engine.level.utils.LevelElement;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Tests for {@link LevelFormatParser} class. */
public class LevelFormatParserTest {

  private static BufferedReader reader(String data) {
    return new BufferedReader(new StringReader(data));
  }

  private static Stream<Arguments> charsAndElements() {
    return Stream.of(
        Arguments.of('F', LevelElement.FLOOR),
        Arguments.of('W', LevelElement.WALL),
        Arguments.of('E', LevelElement.EXIT),
        Arguments.of('S', LevelElement.SKIP),
        Arguments.of('H', LevelElement.HOLE),
        Arguments.of('D', LevelElement.DOOR));
  }

  @Test
  void parseLevelIsAbstractAndDeclaresIOException() throws NoSuchMethodException {
    Method method =
        LevelFormatParser.class.getDeclaredMethod("parseLevel", BufferedReader.class, String.class);

    assertTrue(Modifier.isAbstract(method.getModifiers()));
    assertArrayEquals(new Class<?>[] {IOException.class}, method.getExceptionTypes());
    assertEquals(DungeonLevel.class, method.getReturnType());
  }

  @Test
  void serializeLevelIsAbstract() throws NoSuchMethodException {
    Method method = LevelFormatParser.class.getDeclaredMethod("serializeLevel", DungeonLevel.class);

    assertTrue(Modifier.isAbstract(method.getModifiers()));
    assertEquals(String.class, method.getReturnType());
  }

  @Test
  void readLineSkipsCommentLines() throws IOException {
    BufferedReader reader =
        reader(
            """
            # first comment
            # second comment
              # tab comment
            first line # inline comment
            second line
            """);

    assertEquals("first line", LevelFormatParser.readLine(reader));
  }

  @Test
  void readLineAtEndOfInputReturnsEmptyString() throws IOException {
    assertEquals("", LevelFormatParser.readLine(reader("")));
  }

  @ParameterizedTest
  @MethodSource("charsAndElements")
  void getLevelElementFromCharHappyPath(char c, LevelElement element) {
    assertEquals(element, LevelFormatParser.getLevelElementFromChar(c));
  }

  @Test
  void getLevelElementFromCharUnknownCharThrowsIllegalArgumentException() {
    assertThrows(
        IllegalArgumentException.class, () -> LevelFormatParser.getLevelElementFromChar('X'));
  }

  @ParameterizedTest
  @MethodSource("charsAndElements")
  void getCharFromLevelElementHappyPath(char c, LevelElement element) {
    assertEquals(c, LevelFormatParser.getCharFromLevelElement(element));
  }

  @Test
  void getCharFromLevelElementNullThrowsNullPointerException() {
    assertThrows(NullPointerException.class, () -> LevelFormatParser.getCharFromLevelElement(null));
  }
}
