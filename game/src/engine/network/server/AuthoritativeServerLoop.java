package engine.network.server;

import static engine.network.config.NetworkConfig.FULL_SNAPSHOT_RECOVERY_RETRY_INTERVAL_TICKS;
import static engine.network.config.NetworkConfig.SERVER_DELTA_HISTORY_SIZE;
import static engine.network.config.NetworkConfig.SERVER_DELTA_SNAPSHOT_HZ;
import static engine.network.config.NetworkConfig.SERVER_TICK_HZ;

import engine.Entity;
import engine.Game;
import engine.game.ECSManagement;
import engine.game.PreRunConfiguration;
import engine.level.elements.ILevel;
import engine.level.loader.DungeonLoader;
import engine.network.FullSnapshotSendReason;
import engine.network.NetworkTelemetry;
import engine.network.delta.SnapshotDeltaCompressor;
import engine.network.delta.SnapshotHistory;
import engine.network.messages.s2c.DeltaSnapshotMessage;
import engine.network.messages.s2c.GameOverEvent;
import engine.network.messages.s2c.SnapshotMessage;
import engine.resources.Changes;
import engine.resources.Res;
import engine.resources.Resources;
import engine.utils.Point;
import engine.utils.logging.DungeonLogger;
import feature.entities.HeroBuilder;
import feature.entities.HeroController;
import feature.utils.EntityUtils;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

// TODO: 1. Add missing imports (ILevel, Changes, Res, and Resources)

/**
 * The main server loop for the authoritative multiplayer server.
 *
 * <p>Handles ticking the game state, processing client inputs, and sending snapshots to clients at
 * configured rates.
 *
 * <p>Spawns hero entities for connected clients and removes them on disconnection.
 *
 * <p>Usage:
 *
 * <pre>
 *   ServerTransport serverTransport = new ServerTransport(port);
 *   AuthoritativeServerLoop serverLoop = new AuthoritativeServerLoop(serverTransport);
 *   serverLoop.start();
 *   // ... later when stopping the server
 *   serverLoop.stop();
 * </pre>
 *
 * @see ServerTransport The network transport used for communication.
 * @see HeroController The controller for hero entity actions.
 */
public final class AuthoritativeServerLoop {
  private static final DungeonLogger LOGGER =
      DungeonLogger.getLogger(AuthoritativeServerLoop.class);
  private static final boolean PRINT_RTT = false; // to debug latency issues

  private final ServerTransport net;
  private final ScheduledExecutorService executor;
  private final SnapshotHistory snapshotHistory = new SnapshotHistory(SERVER_DELTA_HISTORY_SIZE);
  private volatile int serverTick = 0;
  private volatile boolean bootstrapComplete;
  private boolean worldInitialized;

  private final Changes levelChanges;
  private boolean firstLevelSeen;

  public AuthoritativeServerLoop(ServerTransport netService) {
    this(netService, Resources.level());
  }

  /**
   * Creates a new AuthoritativeServerLoop that detects level changes of the given handle.
   *
   * <p>Intended for tests, which can pass a fixed or mutable handle instead of the game state.
   *
   * @param netService The ServerTransport to use for network communication.
   * @param level handle to the level whose changes reset the snapshot baselines
   */
  AuthoritativeServerLoop(ServerTransport netService, Res<ILevel> level) {
    this.net = netService;
    this.levelChanges = Changes.of(level);
    this.executor =
        Executors.newSingleThreadScheduledExecutor(
            r -> {
              Thread t = new Thread(r, "AuthoritativeServerLoop");
              t.setDaemon(true);
              return t;
            });
  }

  /**
   * Starts the server loop, initializing necessary components and scheduling tick and snapshot
   * tasks.
   */
  public void start() {
    PreRunConfiguration.frameRate(SERVER_TICK_HZ);

    DungeonLoader.afterAllLevels(
        () -> {
          Game.network().broadcast(new GameOverEvent("All levels completed"), true);
          Game.complete();
        });

    // Nanosecond periods keep the real tick rate at SERVER_TICK_HZ; whole milliseconds would round
    // 60 Hz up to 62.5 Hz while the simulation advances 1/60 s per tick.
    long tickPeriodNanos = TimeUnit.SECONDS.toNanos(1) / SERVER_TICK_HZ;
    long snapshotPeriodNanos = TimeUnit.SECONDS.toNanos(1) / SERVER_DELTA_SNAPSHOT_HZ;

    executor.scheduleAtFixedRate(this::tick, 0, tickPeriodNanos, TimeUnit.NANOSECONDS);
    executor.scheduleAtFixedRate(
        this::sendSnapshot, snapshotPeriodNanos, snapshotPeriodNanos, TimeUnit.NANOSECONDS);

    if (PRINT_RTT) {
      executor.scheduleAtFixedRate(
          () -> {
            for (ClientState state : net.connectedClients()) {
              LOGGER.debug(
                  "Client {}: RTT={}ms, lastActivity={}ms ago",
                  state.clientId(),
                  String.format("%.1f", state.rttEstimateMs()),
                  System.currentTimeMillis() - state.lastActivityTimeMs());
            }
          },
          1000,
          1000,
          TimeUnit.MILLISECONDS);
    }

    LOGGER.info(
        "ServerLoop started: tickHz={}, fullSnapshots={}, deltaSnapshotHz={}",
        SERVER_TICK_HZ,
        "demand-driven",
        SERVER_DELTA_SNAPSHOT_HZ);
  }

  /** Stops the server loop, shutting down the executor service. */
  public void stop() {
    executor.shutdownNow();
    LOGGER.info("ServerLoop stopped");
  }

  /**
   * Checks if the server loop is currently running.
   *
   * @return true if running, false otherwise.
   */
  public boolean isRunning() {
    return !executor.isShutdown();
  }

  /** Allows the server loop to initialize and start ticking the fully constructed initial world. */
  public void completeBootstrap() {
    bootstrapComplete = true;
  }

  private void tick() {
    if (!bootstrapComplete) {
      return;
    }

    try {
      long tickStartNanos = System.nanoTime();
      //noinspection NonAtomicOperationOnVolatileField only place where serverTick is modified
      serverTick++;
      if (!worldInitialized) {
        executeGameTick();
        worldInitialized = true;
        NetworkTelemetry.recordFrameTime(System.nanoTime() - tickStartNanos);
        return;
      }
      // Drain any inbound network messages on the game thread before running systems
      long networkDispatchStartNanos = System.nanoTime();
      try {
        Game.network().pollAndDispatch();
      } catch (Exception e) {
        LOGGER.warn("Error while polling network messages: " + e.getMessage());
      } finally {
        NetworkTelemetry.recordNetworkDispatchBatch(System.nanoTime() - networkDispatchStartNanos);
      }
      syncClientsToEntities();
      executeGameTick();
      NetworkTelemetry.recordFrameTime(System.nanoTime() - tickStartNanos);
    } catch (Exception e) {
      LOGGER.error("Tick error", e);
    } catch (Throwable t) {
      LOGGER.fatal("Unexpected error in server loop", t);
      stop();
    }
  }

  private void executeGameTick() {
    PreRunConfiguration.userOnFrame().execute();
    engine.time.PlayClockRuntime.tick();
    ECSManagement.executeOneTick(engine.System.AuthoritativeSide.SERVER);
  }

  private void sendSnapshot() {
    Set<ClientState> clients = net.connectedClients();
    if (clients.isEmpty()) {
      return;
    }
    clearSnapshotBaselinesOnLevelChange(clients);

    long buildStartNanos = System.nanoTime();
    Objects.requireNonNull(
            Game.network().snapshotTranslator().translateToSnapshot(serverTick),
            "SnapshotTranslator.translateToSnapshot(...) must not return null.")
        .ifPresent(
            snapshot -> {
              NetworkTelemetry.recordSnapshotBuild(System.nanoTime() - buildStartNanos);
              snapshotHistory.add(snapshot, protectedSnapshotTicks(clients));
              NetworkTelemetry.recordSnapshotHistory(
                  snapshot.serverTick(),
                  snapshotHistory.size(),
                  snapshotHistory.capacity(),
                  SERVER_TICK_HZ);
              clients.forEach(client -> sendSnapshotToClient(client, snapshot));
            });
  }

  private void sendSnapshotToClient(ClientState client, SnapshotMessage currentSnapshot) {
    ClientSnapshotSyncState snapshotSync = client.snapshotSync();

    // Snapshot confirmation of client
    int ackTick = snapshotSync.lastAckedSnapshotTick();
    boolean hasAck = snapshotSync.hasAck();

    boolean baselineInHistory = hasAck && snapshotHistory.contains(ackTick);
    NetworkTelemetry.recordBaselineHealth(
        client.clientId(),
        currentSnapshot.serverTick(),
        ackTick,
        baselineInHistory,
        snapshotHistory.capacity(),
        SERVER_TICK_HZ);

    if (!hasAck) {
      if (snapshotSync.fullSnapshotRecoveryDue(
          currentSnapshot.serverTick(), FULL_SNAPSHOT_RECOVERY_RETRY_INTERVAL_TICKS)) {
        sendFullSnapshot(client, currentSnapshot, snapshotSync.pendingFullSnapshotReason());
      }
      return;
    }

    Optional<SnapshotMessage> baseSnapshot = snapshotHistory.snapshot(ackTick);
    if (baseSnapshot.isEmpty()) {
      if (snapshotSync.fullSnapshotRecoveryDue(
          currentSnapshot.serverTick(), FULL_SNAPSHOT_RECOVERY_RETRY_INTERVAL_TICKS)) {
        sendFullSnapshot(client, currentSnapshot, FullSnapshotSendReason.MISSING_BASELINE_HISTORY);
      }
      return;
    }

    SnapshotMessage baseline = baseSnapshot.orElseThrow();
    client.ensureKnownSnapshotEntityIdsForBaseline(baseline);
    SnapshotDeltaCompressor.compress(baseline, currentSnapshot, client.knownSnapshotEntityIds())
        .ifPresent(
            delta -> {
              client.trackKnownSnapshotEntityIds(
                  delta.entityDeltas().stream()
                      .map(entityDelta -> entityDelta.entityId())
                      .toList());
              sendDeltaSnapshot(client, delta);
            });
  }

  private Set<Integer> protectedSnapshotTicks(Set<ClientState> clients) {
    Set<Integer> protectedTicks = new HashSet<>();
    clients.forEach(
        client -> protectedTicks.addAll(client.snapshotSync().protectedSnapshotTicks()));
    return Set.copyOf(protectedTicks);
  }

  private void sendDeltaSnapshot(ClientState client, DeltaSnapshotMessage delta) {
    client.snapshotSync().markDeltaSnapshotSent(delta.serverTick());
    Game.network().send(client.clientId(), delta, false);
  }

  private void sendFullSnapshot(
      ClientState client, SnapshotMessage currentSnapshot, FullSnapshotSendReason reason) {
    ClientSnapshotSyncState snapshotSync = client.snapshotSync();
    snapshotSync.markFullSnapshotScheduled(currentSnapshot.serverTick());
    NetworkTelemetry.recordFullSnapshotScheduled(
        client.clientId(), currentSnapshot.serverTick(), currentSnapshot.entities().size(), reason);
    Game.network()
        .send(client.clientId(), currentSnapshot, true)
        .thenAccept(
            success -> {
              if (success) {
                snapshotSync.markFullSnapshotSent(currentSnapshot.serverTick());
              } else {
                snapshotSync.markFullSnapshotSendFailed(currentSnapshot.serverTick());
                NetworkTelemetry.recordFullSnapshotSendFailed(
                    client.clientId(), currentSnapshot.serverTick());
              }
            });
  }

  private void clearSnapshotBaselinesOnLevelChange(Set<ClientState> clients) {
    // As before, only levels loaded by the DungeonLoader (they have a name) are considered.
    if (currentLevelName().isEmpty()) {
      return;
    }
    if (!levelChanges.changedSinceLastCheck()) {
      return;
    }
    if (!firstLevelSeen) {
      // The first level only becomes the starting point; there is nothing to clear yet.
      firstLevelSeen = true;
      return;
    }
    snapshotHistory.clear();
    clients.forEach(client -> client.clearSnapshotBaseline(FullSnapshotSendReason.LEVEL_CHANGE));
  }

  private Optional<String> currentLevelName() {
    try {
      return Optional.of(DungeonLoader.currentLevel());
    } catch (IndexOutOfBoundsException e) {
      return Optional.empty();
    }
  }

  private void syncClientsToEntities() {
    // Spawn Hero if TCP client exists but no entity is associated
    for (ClientState state : net.establishedClients()) {
      if (state.playerEntity().isEmpty()) {
        state.playerEntity(spawnHeroForClient(state));
      }
    }
  }

  private Entity spawnHeroForClient(ClientState state) {
    Entity hero =
        HeroBuilder.builder()
            .username(state.username())
            .characterClass(state.characterClass())
            .isLocalPlayer(true)
            .build();
    EntityUtils.setPosition(
        hero,
        Game.startTile().map(tile -> tile.position().toCenteredPoint()).orElse(new Point(0, 0)));
    // Adding the hero broadcasts its spawn to all clients, including its owner.
    Game.add(hero);
    return hero;
  }
}
