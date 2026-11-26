package engine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import engine.Component;
import engine.Entity;
import engine.components.PositionComponent;
import engine.utils.EntitySystemMapper;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ECSManagementTest {

  private Map<Class<? extends engine.System>, engine.System> originalSystems;
  private Map<Integer, Entity> originalEntities;
  private Set<EntitySystemMapper> originalFilters;
  private int originalTick;
  private Object originalExecutionSide;
  private boolean originalLevelLoaded;

  private static Field ecsField(String name) throws Exception {
    Field field = ECSManagement.class.getDeclaredField(name);
    field.setAccessible(true);
    return field;
  }

  @SuppressWarnings("unchecked")
  private static <T> T ecsState(String name) throws Exception {
    return (T) ecsField(name).get(null);
  }

  @BeforeEach
  void isolateEcsState() throws Exception {
    Map<Class<? extends engine.System>, engine.System> systems = ecsState("SYSTEMS");
    Map<Integer, Entity> entities = ecsState("allEntities");
    Set<EntitySystemMapper> filters = ecsState("entityFilters");
    originalSystems = new LinkedHashMap<>(systems);
    originalEntities = new LinkedHashMap<>(entities);
    originalFilters = new HashSet<>(filters);
    originalTick = ECSManagement.currentTick();
    originalExecutionSide = ecsState("currentExecutionSide");
    originalLevelLoaded = ecsState("newLevelLoadedThisTick");

    // Detach the original mappers as well: adding test entities must not invoke
    // callbacks on existing systems, including systems that cannot be paused.
    systems.clear();
    entities.clear();
    filters.clear();
    filters.add(new EntitySystemMapper());
    ecsField("currentTick").setInt(null, 0);
    ecsField("currentExecutionSide").set(null, engine.System.AuthoritativeSide.BOTH);
    ecsField("newLevelLoadedThisTick").setBoolean(null, false);
  }

  @AfterEach
  void restoreEcsState() throws Exception {
    // Remove test entities normally so their IDs are unregistered.
    for (Entity entity : ECSManagement.entities().toList()) {
      ECSManagement.remove(entity);
    }
    Map<Class<? extends engine.System>, engine.System> systems = ecsState("SYSTEMS");
    Map<Integer, Entity> entities = ecsState("allEntities");
    Set<EntitySystemMapper> filters = ecsState("entityFilters");
    systems.clear();
    systems.putAll(originalSystems);
    entities.clear();
    entities.putAll(originalEntities);
    filters.clear();
    filters.addAll(originalFilters);
    ecsField("currentTick").setInt(null, originalTick);
    ecsField("currentExecutionSide").set(null, originalExecutionSide);
    ecsField("newLevelLoadedThisTick").setBoolean(null, originalLevelLoaded);
  }

  @Test
  void addedEntityCanBeFoundById() {
    // Arrange
    Entity entity = Entity.createLocalEntity("TestEntity");

    try {
      // Act
      ECSManagement.add(entity);

      // Assert
      assertTrue(ECSManagement.exist(entity));
      assertTrue(ECSManagement.findEntityById(entity.id()).isPresent());
      assertSame(entity, ECSManagement.findEntityById(entity.id()).get());

    } finally {
      // Test-Entity wieder entfernen
      ECSManagement.remove(entity);
    }
  }

  @Test
  void removedEntityCanNoLongerBeFound() {
    // Arrange
    Entity entity = Entity.createLocalEntity("TestEntity");
    ECSManagement.add(entity);

    try {
      // Act
      ECSManagement.remove(entity);

      // Assert
      assertFalse(ECSManagement.exist(entity));
      assertTrue(ECSManagement.findEntityById(entity.id()).isEmpty());

    } finally {
      // Sicherstellen, dass keine Test-Entity zurückbleibt
      ECSManagement.remove(entity);
    }
  }

  @Test
  void addingEntityTwiceThrowsException() {
    // Arrange
    Entity entity = Entity.createLocalEntity("TestEntity");

    try {
      ECSManagement.add(entity);

      // Act & Assert
      assertThrows(IllegalArgumentException.class, () -> ECSManagement.add(entity));

      // Die ursprüngliche Entity muss weiterhin vorhanden sein
      assertTrue(ECSManagement.exist(entity));

    } finally {
      // Test-Entity wieder entfernen
      ECSManagement.remove(entity);
    }
  }

  @Test
  void entitiesAreFilteredByComponents() {
    // Arrange
    Entity entityWithPosition = Entity.createLocalEntity("WithPosition");
    Entity entityWithoutPosition = Entity.createLocalEntity("WithoutPosition");

    entityWithPosition.add(new PositionComponent());

    try {
      ECSManagement.add(entityWithPosition);
      ECSManagement.add(entityWithoutPosition);

      // Act
      Set<Class<? extends Component>> filter = Set.of(PositionComponent.class);

      List<Entity> result = ECSManagement.entities(filter).toList();

      // Assert
      assertTrue(result.contains(entityWithPosition));
      assertFalse(result.contains(entityWithoutPosition));

    } finally {
      // Test-Entities wieder entfernen
      ECSManagement.remove(entityWithPosition);
      ECSManagement.remove(entityWithoutPosition);
    }
  }

  @Test
  void entityFilterUpdatesWhenComponentsChange() {
    // Arrange
    Entity entity = Entity.createLocalEntity("TestEntity");
    Set<Class<? extends Component>> filter = Set.of(PositionComponent.class);

    try {
      ECSManagement.add(entity);

      // Assert: Entity besitzt noch keine PositionComponent
      assertFalse(ECSManagement.entities(filter).anyMatch(e -> e == entity));

      // Act: Komponente hinzufügen
      entity.add(new PositionComponent());

      // Assert: Entity muss jetzt im Filter enthalten sein
      assertTrue(ECSManagement.entities(filter).anyMatch(e -> e == entity));

      // Act: Komponente entfernen
      entity.remove(PositionComponent.class);

      // Assert: Entity darf nicht mehr im Filter enthalten sein
      assertFalse(ECSManagement.entities(filter).anyMatch(e -> e == entity));

    } finally {
      // Test-Entity wieder entfernen
      ECSManagement.remove(entity);
    }
  }

  private static class TestSystem extends engine.System {

    @Override
    public void execute() {
      // Keine Spiellogik für diesen Test erforderlich
    }
  }

  @Test
  void addedSystemIsRegistered() {
    // Arrange
    TestSystem system = new TestSystem();

    try {
      // Act
      ECSManagement.add(system);

      // Assert
      assertTrue(ECSManagement.systems().containsKey(TestSystem.class));
      assertSame(system, ECSManagement.systems().get(TestSystem.class));

    } finally {
      // Testsystem wieder entfernen
      ECSManagement.remove(TestSystem.class);
    }
  }

  @Test
  void addingSystemOfSameClassReplacesPreviousSystem() {
    // Arrange
    TestSystem firstSystem = new TestSystem();
    TestSystem secondSystem = new TestSystem();

    try {
      ECSManagement.add(firstSystem);

      // Act
      Optional<engine.System> previousSystem = ECSManagement.add(secondSystem);

      // Assert
      assertTrue(previousSystem.isPresent());
      assertSame(firstSystem, previousSystem.get());
      assertSame(secondSystem, ECSManagement.systems().get(TestSystem.class));

    } finally {
      // Testsystem wieder entfernen
      ECSManagement.remove(TestSystem.class);
    }
  }

  @Test
  void removedSystemIsNoLongerRegistered() {
    // Arrange
    TestSystem system = new TestSystem();

    try {
      ECSManagement.add(system);

      // Sicherstellen, dass das System registriert ist
      assertTrue(ECSManagement.systems().containsKey(TestSystem.class));

      // Act
      ECSManagement.remove(TestSystem.class);

      // Assert
      assertFalse(ECSManagement.systems().containsKey(TestSystem.class));

    } finally {
      // Sicherstellen, dass das Testsystem entfernt bleibt
      ECSManagement.remove(TestSystem.class);
    }
  }

  @Test
  void isolatedTickRestoresTickCounter() throws Exception {
    // Arrange
    CountingTestSystem system = new CountingTestSystem();
    int tickBefore = ECSManagement.currentTick();

    // Act
    executeIsolatedTick(system, engine.System.AuthoritativeSide.SERVER);

    // Assert
    assertEquals(tickBefore, ECSManagement.currentTick());
    assertEquals(1, system.executionCount());
  }

  @Test
  void newSystemIsRunningByDefault() {
    // Arrange
    TestSystem system = new TestSystem();

    // Act
    boolean running = system.isRunning();

    // Assert
    assertTrue(running);
  }

  @Test
  void stoppedSystemCanBeRestarted() {
    // Arrange
    TestSystem system = new TestSystem();

    // Act
    system.stop();

    // Assert
    assertFalse(system.isRunning());

    // Act
    system.run();

    // Assert
    assertTrue(system.isRunning());
  }

  private static class IntervalTestSystem extends engine.System {

    IntervalTestSystem() {
      super(engine.System.AuthoritativeSide.SERVER, 3);
    }

    @Override
    public void execute() {
      // Keine Spiellogik erforderlich
    }
  }

  @Test
  void systemHasConfiguredExecutionInterval() {
    // Arrange
    IntervalTestSystem system = new IntervalTestSystem();

    // Act
    int interval = system.executeEveryXFrames();

    // Assert
    assertEquals(3, interval);
  }

  private static class CountingTestSystem extends engine.System {

    private int executionCount = 0;

    CountingTestSystem() {
      super(engine.System.AuthoritativeSide.SERVER, 1);
    }

    @Override
    public void execute() {
      executionCount++;
    }

    int executionCount() {
      return executionCount;
    }
  }

  @Test
  void stoppedSystemIsNotExecutedDuringTick() throws Exception {
    CountingTestSystem system = new CountingTestSystem();
    system.stop();
    executeIsolatedTick(system, engine.System.AuthoritativeSide.SERVER);
    assertEquals(0, system.executionCount());
  }

  private int executeIsolatedTick(engine.System testSystem, engine.System.AuthoritativeSide side)
      throws Exception {
    int tickBefore = ECSManagement.currentTick();
    Object sideBefore = ecsState("currentExecutionSide");
    boolean levelLoadedBefore = ecsState("newLevelLoadedThisTick");
    try {
      ECSManagement.add(testSystem);
      ECSManagement.executeOneTick(side);
      return ECSManagement.currentTick();
    } finally {
      ECSManagement.remove(testSystem.getClass());
      ecsField("currentTick").setInt(null, tickBefore);
      ecsField("currentExecutionSide").set(null, sideBefore);
      ecsField("newLevelLoadedThisTick").setBoolean(null, levelLoadedBefore);
    }
  }

  @Test
  void runningSystemIsExecutedDuringTick() throws Exception {
    // Arrange
    CountingTestSystem system = new CountingTestSystem();

    // Act
    executeIsolatedTick(system, engine.System.AuthoritativeSide.SERVER);

    // Assert
    assertEquals(1, system.executionCount());
  }

  private static class CountingIntervalTestSystem extends engine.System {

    private int executionCount = 0;

    CountingIntervalTestSystem() {
      super(engine.System.AuthoritativeSide.SERVER, 3);
    }

    @Override
    public void execute() {
      executionCount++;
    }

    int executionCount() {
      return executionCount;
    }
  }

  @Test
  void systemExecutesOnlyEveryThirdTick() throws Exception {
    // Arrange
    CountingIntervalTestSystem system = new CountingIntervalTestSystem();

    // Act: Erster Tick
    executeIsolatedTick(system, engine.System.AuthoritativeSide.SERVER);

    // Assert: Noch keine Ausführung
    assertEquals(0, system.executionCount());

    // Act: Zweiter Tick
    executeIsolatedTick(system, engine.System.AuthoritativeSide.SERVER);

    // Assert: Noch keine Ausführung
    assertEquals(0, system.executionCount());

    // Act: Dritter Tick
    executeIsolatedTick(system, engine.System.AuthoritativeSide.SERVER);

    // Assert: Genau eine Ausführung
    assertEquals(1, system.executionCount());
  }

  @Test
  void serverSystemIsNotExecutedDuringClientTick() throws Exception {
    // Arrange
    CountingTestSystem system = new CountingTestSystem();

    // Act
    executeIsolatedTick(system, engine.System.AuthoritativeSide.CLIENT);

    // Assert
    assertEquals(0, system.executionCount());
  }

  private static class ClientCountingTestSystem extends engine.System {

    private int executionCount = 0;

    ClientCountingTestSystem() {
      super(engine.System.AuthoritativeSide.CLIENT, 1);
    }

    @Override
    public void execute() {
      executionCount++;
    }

    int executionCount() {
      return executionCount;
    }
  }

  @Test
  void clientSystemIsNotExecutedDuringServerTick() throws Exception {
    // Arrange
    ClientCountingTestSystem system = new ClientCountingTestSystem();

    // Act
    executeIsolatedTick(system, engine.System.AuthoritativeSide.SERVER);

    // Assert
    assertEquals(0, system.executionCount());
  }

  @Test
  void clientSystemIsExecutedDuringClientTick() throws Exception {
    // Arrange
    ClientCountingTestSystem system = new ClientCountingTestSystem();

    // Act
    executeIsolatedTick(system, engine.System.AuthoritativeSide.CLIENT);

    // Assert
    assertEquals(1, system.executionCount());
  }

  private static class BothCountingTestSystem extends engine.System {

    private int executionCount = 0;

    BothCountingTestSystem() {
      super(engine.System.AuthoritativeSide.BOTH, 1);
    }

    @Override
    public void execute() {
      executionCount++;
    }

    int executionCount() {
      return executionCount;
    }
  }

  @Test
  void bothSystemIsExecutedOnClientAndServer() throws Exception {
    // Arrange
    BothCountingTestSystem system = new BothCountingTestSystem();

    // Act: CLIENT-Tick
    executeIsolatedTick(system, engine.System.AuthoritativeSide.CLIENT);

    // Assert
    assertEquals(1, system.executionCount());

    // Act: SERVER-Tick
    executeIsolatedTick(system, engine.System.AuthoritativeSide.SERVER);

    // Assert
    assertEquals(2, system.executionCount());
  }

  @Test
  void tickCounterIncreasesAfterExecution() throws Exception {
    // Arrange
    CountingTestSystem system = new CountingTestSystem();
    int tickBefore = ECSManagement.currentTick();

    // Act
    int tickAfter = executeIsolatedTick(system, engine.System.AuthoritativeSide.SERVER);

    // Assert
    assertEquals(tickBefore + 1, tickAfter);
    assertEquals(tickBefore, ECSManagement.currentTick());
  }

  private static class FirstOrderTestSystem extends engine.System {

    private final List<String> executionOrder;

    FirstOrderTestSystem(List<String> executionOrder) {
      this.executionOrder = executionOrder;
    }

    @Override
    public void execute() {
      executionOrder.add("first");
    }
  }

  private static class SecondOrderTestSystem extends engine.System {

    private final List<String> executionOrder;

    SecondOrderTestSystem(List<String> executionOrder) {
      this.executionOrder = executionOrder;
    }

    @Override
    public void execute() {
      executionOrder.add("second");
    }
  }

  @Test
  void systemsExecuteInRegistrationOrder() {
    List<String> executionOrder = new java.util.ArrayList<>();
    ECSManagement.add(new FirstOrderTestSystem(executionOrder));
    ECSManagement.add(new SecondOrderTestSystem(executionOrder));
    ECSManagement.executeOneTick(engine.System.AuthoritativeSide.SERVER);
    assertEquals(List.of("first", "second"), executionOrder);
  }

  @Test
  void tickStopsWhenNewLevelWasLoaded() throws Exception {
    CountingTestSystem system = new CountingTestSystem();
    ECSManagement.add(system);
    ecsField("newLevelLoadedThisTick").setBoolean(null, true);
    int tickBefore = ECSManagement.currentTick();
    ECSManagement.executeOneTick(engine.System.AuthoritativeSide.SERVER);
    assertEquals(0, system.executionCount());
    assertEquals(tickBefore + 1, ECSManagement.currentTick());
  }
}
