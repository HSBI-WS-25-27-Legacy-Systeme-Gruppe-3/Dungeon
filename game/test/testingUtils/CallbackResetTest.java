package testingUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import engine.game.PreRunConfiguration;
import engine.game.WindowEventManager;
import engine.network.DefaultSnapshotTranslator;
import engine.network.SnapshotTranslator;
import engine.network.config.EntitySpawnStrategy;
import engine.network.config.NetworkConfig;
import engine.network.input.InputCommandRouter;
import engine.utils.IVoidFunction;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CallbackResetTest {

  private final CallbackReset reset = new CallbackReset();

  private IVoidFunction frame;
  private IVoidFunction setup;
  private Consumer<Boolean> levelLoad;
  private SnapshotTranslator translator;
  private EntitySpawnStrategy spawn;

  @BeforeEach
  void save() {
    frame = PreRunConfiguration.userOnFrame();
    setup = PreRunConfiguration.userOnSetup();
    levelLoad = PreRunConfiguration.userOnLevelLoad();
    translator = NetworkConfig.SNAPSHOT_TRANSLATOR;
    spawn = NetworkConfig.ENTITY_SPAWN_STRATEGY;
  }

  @AfterEach
  void restore() {
    PreRunConfiguration.userOnFrame(frame);
    PreRunConfiguration.userOnSetup(setup);
    PreRunConfiguration.userOnLevelLoad(levelLoad);
    NetworkConfig.SNAPSHOT_TRANSLATOR = translator;
    NetworkConfig.ENTITY_SPAWN_STRATEGY = spawn;
  }

  @Test
  void clearsUserCallbacks() {
    AtomicBoolean called = new AtomicBoolean(false);
    PreRunConfiguration.userOnFrame(() -> called.set(true));

    reset.before();
    PreRunConfiguration.userOnFrame().execute();

    assertFalse(called.get());
  }

  @Test
  void resetsNetworkStrategiesToDefault() {
    SnapshotTranslator custom = new DefaultSnapshotTranslator();
    NetworkConfig.SNAPSHOT_TRANSLATOR = custom;

    reset.before();

    assertNotSame(custom, NetworkConfig.SNAPSHOT_TRANSLATOR);
    assertInstanceOf(DefaultSnapshotTranslator.class, NetworkConfig.SNAPSHOT_TRANSLATOR);
  }

  @Test
  void restoresCallbacksAndStrategiesAfterTheTest() {
    IVoidFunction marker = () -> {};
    SnapshotTranslator custom = new DefaultSnapshotTranslator();
    PreRunConfiguration.userOnFrame(marker);
    NetworkConfig.SNAPSHOT_TRANSLATOR = custom;

    reset.before();
    reset.after();

    assertSame(marker, PreRunConfiguration.userOnFrame());
    assertSame(custom, NetworkConfig.SNAPSHOT_TRANSLATOR);
  }

  @Test
  void clearsWindowListenersAndInputHandlers() throws Exception {
    WindowEventManager.registerWindowRefreshListener(() -> {});
    InputCommandRouter.register("test:ping", false, context -> {});

    reset.before();

    assertTrue(windowRefreshListeners().isEmpty());
    assertTrue(InputCommandRouter.registrations().isEmpty());
  }

  private static List<?> windowRefreshListeners() throws Exception {
    Field field = WindowEventManager.class.getDeclaredField("windowRefreshListeners");
    field.setAccessible(true);
    return (List<?>) field.get(null);
  }
}
