package testingUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import engine.level.DungeonLevel;
import engine.level.loader.DungeonLoader;
import engine.utils.EntityIdProvider;
import java.lang.reflect.Field;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class IdentifierResetTest {

  private final IdentifierReset reset = new IdentifierReset();

  @Test
  void entityIdsStartFromTheBeginningAgain() {
    EntityIdProvider.nextId();
    EntityIdProvider.nextId();
    EntityIdProvider.nextLocalId();

    reset.before();

    assertEquals(0, EntityIdProvider.nextId());
    assertEquals(-1, EntityIdProvider.nextLocalId());
  }

  @Test
  void levelNameSuffixStartsAtOne() throws Exception {
    suffix(9);

    reset.before();

    assertEquals(1, suffix());
  }

  @Test
  void dungeonLoaderHasNoCurrentLevelOrVariant() throws Exception {
    setCurrent(5, OptionalInt.of(2), "levels/forest.lvl");

    reset.before();

    assertEquals(-1, DungeonLoader.currentLevelIndex());
    assertTrue(DungeonLoader.currentVariantIndex().isEmpty());
    assertFalse(DungeonLoader.currentLevelAssetPath().isPresent());
  }

  private static int suffix() throws Exception {
    Field field = DungeonLevel.class.getDeclaredField("levelNameSuffix");
    field.setAccessible(true);
    return field.getInt(null);
  }

  private static void suffix(int value) throws Exception {
    Field field = DungeonLevel.class.getDeclaredField("levelNameSuffix");
    field.setAccessible(true);
    field.setInt(null, value);
  }

  private static void setCurrent(int level, OptionalInt variant, String assetPath)
      throws Exception {
    Field current = DungeonLoader.class.getDeclaredField("currentLevel");
    current.setAccessible(true);
    current.setInt(null, level);
    Field variantField = DungeonLoader.class.getDeclaredField("currentVariant");
    variantField.setAccessible(true);
    variantField.set(null, variant);
    Field assetField = DungeonLoader.class.getDeclaredField("currentLevelAssetPath");
    assetField.setAccessible(true);
    assetField.set(null, assetPath);
  }
}
