package testingUtils;

import engine.level.DungeonLevel;
import engine.level.loader.DungeonLoader;
import engine.utils.EntityIdProvider;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Setzt vor jedem Test die ID-Zähler der Engine und die Level-Zeiger auf ihren Anfangswert: der
 * Entity-ID-Zähler beginnt wieder bei 0 bzw. -1, der Namenszähler der Level bei 1, und der
 * DungeonLoader hat keinen aktuellen Level und keine aktuelle Variante.
 */
public final class IdentifierReset {

  /** Setzt die ID-Zähler und die aktuellen Level-Zeiger zurück. */
  public void before() {
    resetEntityIds();
    resetLevelNameSuffix();
    resetDungeonLoader();
  }

  /** Nach dem Test ist nichts zu tun, der nächste Test setzt die Zähler wieder zurück. */
  public void after() {}

  private static void resetEntityIds() {
    try {
      atomic("NEXT").set(0);
      atomic("LOCAL_NEXT").set(-1);
      Field used = EntityIdProvider.class.getDeclaredField("USED");
      used.setAccessible(true);
      ((Map<?, ?>) used.get(null)).clear();
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Entity-IDs konnten nicht zurückgesetzt werden", e);
    }
  }

  private static AtomicInteger atomic(String name) throws ReflectiveOperationException {
    Field field = EntityIdProvider.class.getDeclaredField(name);
    field.setAccessible(true);
    return (AtomicInteger) field.get(null);
  }

  private static void resetLevelNameSuffix() {
    try {
      Field field = DungeonLevel.class.getDeclaredField("levelNameSuffix");
      field.setAccessible(true);
      field.setInt(null, 1);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Level-Namenszähler konnte nicht zurückgesetzt werden", e);
    }
  }

  private static void resetDungeonLoader() {
    try {
      Field current = DungeonLoader.class.getDeclaredField("currentLevel");
      current.setAccessible(true);
      current.setInt(null, -1);
      Field variant = DungeonLoader.class.getDeclaredField("currentVariant");
      variant.setAccessible(true);
      variant.set(null, OptionalInt.empty());
      Field assetPath = DungeonLoader.class.getDeclaredField("currentLevelAssetPath");
      assetPath.setAccessible(true);
      assetPath.set(null, null);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("DungeonLoader konnte nicht zurückgesetzt werden", e);
    }
  }
}
