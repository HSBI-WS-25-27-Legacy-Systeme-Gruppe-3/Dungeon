package testingUtils;

import engine.game.PreRunConfiguration;
import engine.game.WindowEventManager;
import engine.network.DefaultSnapshotTranslator;
import engine.network.SnapshotTranslator;
import engine.network.config.DefaultEntitySpawnStrategy;
import engine.network.config.EntitySpawnStrategy;
import engine.network.config.NetworkConfig;
import engine.network.input.InputCommandRouter;
import engine.utils.IVoidFunction;
import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * Setzt vor jedem Test die global registrierten Callbacks, Listener und Strategien zurück: die
 * Nutzer-Callbacks in {@link PreRunConfiguration} werden geleert, die Fenster-Listener und die
 * Eingabe-Handler entfernt und die Netzwerk-Strategien auf ihre Standardimplementierung gesetzt.
 * Die vorher gesetzten Callbacks und Strategien werden nach dem Test wiederhergestellt.
 */
public final class CallbackReset {

  private IVoidFunction userOnFrame;
  private IVoidFunction userOnSetup;
  private Consumer<Boolean> userOnLevelLoad;
  private SnapshotTranslator snapshotTranslator;
  private EntitySpawnStrategy entitySpawnStrategy;

  /** Merkt sich die gesetzten Callbacks und Strategien und setzt den Ausgangszustand. */
  public void before() {
    userOnFrame = PreRunConfiguration.userOnFrame();
    userOnSetup = PreRunConfiguration.userOnSetup();
    userOnLevelLoad = PreRunConfiguration.userOnLevelLoad();
    snapshotTranslator = NetworkConfig.SNAPSHOT_TRANSLATOR;
    entitySpawnStrategy = NetworkConfig.ENTITY_SPAWN_STRATEGY;

    PreRunConfiguration.userOnFrame(() -> {});
    PreRunConfiguration.userOnSetup(() -> {});
    PreRunConfiguration.userOnLevelLoad(loaded -> {});
    NetworkConfig.SNAPSHOT_TRANSLATOR = new DefaultSnapshotTranslator();
    NetworkConfig.ENTITY_SPAWN_STRATEGY = new DefaultEntitySpawnStrategy();

    WindowEventManager.clearAllListeners();
    resetInputCommandRouter();
  }

  /** Stellt die Callbacks und Strategien von vor dem Test wieder her. */
  public void after() {
    PreRunConfiguration.userOnFrame(userOnFrame);
    PreRunConfiguration.userOnSetup(userOnSetup);
    PreRunConfiguration.userOnLevelLoad(userOnLevelLoad);
    NetworkConfig.SNAPSHOT_TRANSLATOR = snapshotTranslator;
    NetworkConfig.ENTITY_SPAWN_STRATEGY = entitySpawnStrategy;
  }

  private static void resetInputCommandRouter() {
    try {
      Method reset = InputCommandRouter.class.getDeclaredMethod("resetForTesting");
      reset.setAccessible(true);
      reset.invoke(null);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Eingabe-Handler konnten nicht zurückgesetzt werden", e);
    }
  }
}
