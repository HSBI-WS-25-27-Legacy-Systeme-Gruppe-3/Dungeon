package engine.game;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

import engine.Component;
import engine.components.PositionComponent;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import engine.Entity;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.lang.reflect.Field;
import java.util.HashMap;

class ECSManagementTest {

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
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ECSManagement.add(entity));

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
        executeIsolatedTick(
                system,
                engine.System.AuthoritativeSide.SERVER);

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
    void stoppedSystemIsNotExecutedDuringTick() {
        // Arrange
        Map<Class<? extends engine.System>, engine.System> existingSystems = ECSManagement.systems();

        Map<engine.System, Boolean> runningStates = new java.util.HashMap<>();
        Map<engine.System, Integer> frameCounters = new java.util.HashMap<>();

        for (engine.System system : existingSystems.values()) {
            runningStates.put(system, system.isRunning());
            frameCounters.put(system, system.lastExecuteInFrames());
            system.stop();
        }

        CountingTestSystem testSystem = new CountingTestSystem();
        testSystem.stop();

        try {
            ECSManagement.add(testSystem);

            // Act
            ECSManagement.executeOneTick(engine.System.AuthoritativeSide.SERVER);

            // Assert
            assertEquals(0, testSystem.executionCount());

        } finally {
            ECSManagement.remove(CountingTestSystem.class);

            // Ursprüngliche Zustände wiederherstellen
            for (engine.System system : existingSystems.values()) {
                system.lastExecuteInFrames(frameCounters.get(system));

                if (runningStates.get(system)) {
                    system.run();
                } else {
                    system.stop();
                }
            }
        }
    }

    private int executeIsolatedTick(
            engine.System testSystem,
            engine.System.AuthoritativeSide side) throws Exception {

        Map<Class<? extends engine.System>, engine.System> existingSystems = ECSManagement.systems();

        Map<engine.System, Boolean> runningStates = new HashMap<>();
        Map<engine.System, Integer> frameCounters = new HashMap<>();

        Field tickField = ECSManagement.class.getDeclaredField("currentTick");
        tickField.setAccessible(true);
        int originalTick = tickField.getInt(null);

        for (engine.System system : existingSystems.values()) {
            runningStates.put(system, system.isRunning());
            frameCounters.put(system, system.lastExecuteInFrames());
            system.stop();
        }

        try {
            ECSManagement.add(testSystem);
            ECSManagement.executeOneTick(side);

            return ECSManagement.currentTick();

        } finally {
            ECSManagement.remove(testSystem.getClass());

            for (engine.System system : existingSystems.values()) {
                system.lastExecuteInFrames(frameCounters.get(system));

                if (runningStates.get(system)) {
                    system.run();
                } else {
                    system.stop();
                }
            }

            tickField.setInt(null, originalTick);
        }
    }

    @Test
    void runningSystemIsExecutedDuringTick() throws Exception {
        // Arrange
        CountingTestSystem system = new CountingTestSystem();

        // Act
        executeIsolatedTick(
                system,
                engine.System.AuthoritativeSide.SERVER);

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
        executeIsolatedTick(
                system,
                engine.System.AuthoritativeSide.CLIENT);

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
        executeIsolatedTick(
                system,
                engine.System.AuthoritativeSide.SERVER);

        // Assert
        assertEquals(0, system.executionCount());
    }

    @Test
    void clientSystemIsExecutedDuringClientTick() throws Exception {
        // Arrange
        ClientCountingTestSystem system = new ClientCountingTestSystem();

        // Act
        executeIsolatedTick(
                system,
                engine.System.AuthoritativeSide.CLIENT);

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
        executeIsolatedTick(
                system,
                engine.System.AuthoritativeSide.CLIENT);

        // Assert
        assertEquals(1, system.executionCount());

        // Act: SERVER-Tick
        executeIsolatedTick(
                system,
                engine.System.AuthoritativeSide.SERVER);

        // Assert
        assertEquals(2, system.executionCount());
    }

    @Test
    void tickCounterIncreasesAfterExecution() throws Exception {
        // Arrange
        CountingTestSystem system = new CountingTestSystem();
        int tickBefore = ECSManagement.currentTick();

        // Act
        int tickAfter = executeIsolatedTick(
                system,
                engine.System.AuthoritativeSide.SERVER);

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
    void systemsExecuteInRegistrationOrder() throws Exception {
        // Arrange
        List<String> executionOrder = new java.util.ArrayList<>();

        FirstOrderTestSystem first = new FirstOrderTestSystem(executionOrder);
        SecondOrderTestSystem second = new SecondOrderTestSystem(executionOrder);

        Map<Class<? extends engine.System>, engine.System> existingSystems = ECSManagement.systems();

        Map<engine.System, Boolean> runningStates = new HashMap<>();
        Map<engine.System, Integer> frameCounters = new HashMap<>();

        Field tickField = ECSManagement.class.getDeclaredField("currentTick");
        tickField.setAccessible(true);
        int originalTick = tickField.getInt(null);

        for (engine.System system : existingSystems.values()) {
            runningStates.put(system, system.isRunning());
            frameCounters.put(system, system.lastExecuteInFrames());
            system.stop();
        }

        try {
            // Act
            ECSManagement.add(first);
            ECSManagement.add(second);

            ECSManagement.executeOneTick(engine.System.AuthoritativeSide.SERVER);

            // Assert
            assertEquals(List.of("first", "second"), executionOrder);

        } finally {
            ECSManagement.remove(FirstOrderTestSystem.class);
            ECSManagement.remove(SecondOrderTestSystem.class);

            for (engine.System system : existingSystems.values()) {
                system.lastExecuteInFrames(frameCounters.get(system));

                if (runningStates.get(system)) {
                    system.run();
                } else {
                    system.stop();
                }
            }

            tickField.setInt(null, originalTick);
        }
    }

    @Test
    void tickStopsWhenNewLevelWasLoaded() throws Exception {
        // Arrange
        CountingTestSystem system = new CountingTestSystem();

        Field levelLoadedField = ECSManagement.class.getDeclaredField("newLevelLoadedThisTick");
        levelLoadedField.setAccessible(true);

        Field tickField = ECSManagement.class.getDeclaredField("currentTick");
        tickField.setAccessible(true);

        boolean originalLevelLoaded = levelLoadedField.getBoolean(null);
        int originalTick = tickField.getInt(null);

        Map<Class<? extends engine.System>, engine.System> existingSystems = ECSManagement.systems();

        Map<engine.System, Boolean> runningStates = new HashMap<>();
        Map<engine.System, Integer> frameCounters = new HashMap<>();

        try {
            // Vorhandene Systeme anhalten
            for (engine.System existingSystem : existingSystems.values()) {
                runningStates.put(existingSystem, existingSystem.isRunning());
                frameCounters.put(
                        existingSystem, existingSystem.lastExecuteInFrames());
                existingSystem.stop();
            }

            ECSManagement.add(system);

            // Levelwechsel simulieren
            levelLoadedField.setBoolean(null, true);

            // Act
            ECSManagement.executeOneTick(
                    engine.System.AuthoritativeSide.SERVER);

            // Assert
            assertEquals(0, system.executionCount());
            assertEquals(originalTick + 1, ECSManagement.currentTick());

        } finally {
            ECSManagement.remove(CountingTestSystem.class);

            // Ursprüngliche Systemzustände wiederherstellen
            for (engine.System existingSystem : existingSystems.values()) {
                existingSystem.lastExecuteInFrames(
                        frameCounters.get(existingSystem));

                if (runningStates.get(existingSystem)) {
                    existingSystem.run();
                } else {
                    existingSystem.stop();
                }
            }

            // Globale Zustände wiederherstellen
            levelLoadedField.setBoolean(null, originalLevelLoaded);
            tickField.setInt(null, originalTick);
        }
    }
}