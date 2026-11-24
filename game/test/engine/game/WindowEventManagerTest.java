package engine.game;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WindowEventManagerTest {

    @BeforeEach
    void setUp() {
        WindowEventManager.clearAllListeners();
    }

    @AfterEach
    void tearDown() {
        WindowEventManager.clearAllListeners();
    }

    @Test
    void registeredFocusListenerReceivesFocusGainedEvent() {
        // Arrange: Listener registrieren
        boolean[] focusReceived = { false };

        WindowEventManager.registerFocusChangeListener(
                focused -> focusReceived[0] = focused);

        // Act: Fokusereignis auslösen
        WindowEventManager.windowListener().focusGained();

        // Assert: Ergebnis überprüfen
        assertTrue(focusReceived[0]);
    }

    @Test
    void unregisteredFocusListenerIsNotCalled() {
        // Arrange
        boolean[] called = { false };

        Consumer<Boolean> listener = focused -> called[0] = true;

        WindowEventManager.registerFocusChangeListener(listener);
        WindowEventManager.unregisterFocusChangeListener(listener);

        // Act
        WindowEventManager.windowListener().focusGained();

        // Assert
        assertFalse(called[0]);
    }

    @Test
    void unregisterFocusListenerReturnsCorrectResult() {
        Consumer<Boolean> listener = focused -> {
        };

        WindowEventManager.registerFocusChangeListener(listener);

        assertTrue(WindowEventManager.unregisterFocusChangeListener(listener));
        assertFalse(WindowEventManager.unregisterFocusChangeListener(listener));
    }

    @Test
    void registeredFocusListenerReceivesFocusLostEvent() {
        // Arrange
        boolean[] focusReceived = { true };

        WindowEventManager.registerFocusChangeListener(
                focused -> focusReceived[0] = focused);

        // Act
        WindowEventManager.windowListener().focusLost();

        // Assert
        assertFalse(focusReceived[0]);
    }

    @Test
    void registeredIconificationListenerReceivesMinimizedEvent() {
        // Arrange
        boolean[] minimized = { false };

        WindowEventManager.registerIconificationListener(
                iconified -> minimized[0] = iconified);

        // Act
        WindowEventManager.windowListener().iconified(true);

        // Assert
        assertTrue(minimized[0]);
    }

    @Test
    void registeredIconificationListenerReceivesRestoredEvent() {
        // Arrange
        boolean[] minimized = { true };

        WindowEventManager.registerIconificationListener(
                iconified -> minimized[0] = iconified);

        // Act
        WindowEventManager.windowListener().iconified(false);

        // Assert
        assertFalse(minimized[0]);
    }

    @Test
    void registeredMaximizationListenerReceivesMaximizedEvent() {
        // Arrange
        boolean[] maximized = { false };

        WindowEventManager.registerMaximizationListener(
                value -> maximized[0] = value);

        // Act
        WindowEventManager.windowListener().maximized(true);

        // Assert
        assertTrue(maximized[0]);
    }

    @Test
    void registeredMaximizationListenerReceivesRestoredEvent() {
        // Arrange
        boolean[] maximized = { true };

        WindowEventManager.registerMaximizationListener(
                value -> maximized[0] = value);

        // Act
        WindowEventManager.windowListener().maximized(false);

        // Assert
        assertFalse(maximized[0]);
    }

    @Test
    void closeRequestWithoutListenersIsAllowed() {
        assertTrue(WindowEventManager.windowListener().closeRequested());
    }

    @Test
    void closeRequestIsAllowedWhenAllListenersApprove() {
        WindowEventManager.registerCloseRequestListener(() -> true);
        WindowEventManager.registerCloseRequestListener(() -> true);

        assertTrue(WindowEventManager.windowListener().closeRequested());
    }

    @Test
    void closeRequestIsDeniedWhenOneListenerRejects() {
        WindowEventManager.registerCloseRequestListener(() -> true);
        WindowEventManager.registerCloseRequestListener(() -> false);

        assertFalse(WindowEventManager.windowListener().closeRequested());
    }

    @Test
    void refreshRequestedCallsAllRegisteredListeners() {
        // Arrange
        int[] callCount = { 0 };

        WindowEventManager.registerWindowRefreshListener(
                () -> callCount[0]++);

        WindowEventManager.registerWindowRefreshListener(
                () -> callCount[0]++);

        // Act
        WindowEventManager.windowListener().refreshRequested();

        // Assert
        assertEquals(2, callCount[0]);
    }

    @Test
    void filesDroppedListenerReceivesFilePaths() {
        // Arrange
        String[] expectedFiles = { "test.txt", "image.png" };
        String[][] receivedFiles = { null };

        WindowEventManager.registerFilesDroppedListener(
                files -> receivedFiles[0] = files);

        // Act
        WindowEventManager.windowListener().filesDropped(expectedFiles);

        // Assert
        assertArrayEquals(expectedFiles, receivedFiles[0]);
    }

    @Test
    void windowCreatedListenerIsCalled() {
        // Arrange
        boolean[] called = { false };

        WindowEventManager.registerWindowCreatedListener(
                window -> called[0] = true);

        // Act
        WindowEventManager.windowListener().created(null);

        // Assert
        assertTrue(called[0]);
    }

    @Test
    void clearAllListenersRemovesRegisteredListeners() {
        // Arrange
        int[] callCount = { 0 };

        WindowEventManager.registerWindowRefreshListener(
                () -> callCount[0]++);

        // Act
        WindowEventManager.clearAllListeners();
        WindowEventManager.windowListener().refreshRequested();

        // Assert
        assertEquals(0, callCount[0]);
    }

    @Test
    void nullFocusListenerIsIgnored() {
        // Arrange
        WindowEventManager.registerFocusChangeListener(null);

        // Act & Assert
        assertDoesNotThrow(
                () -> WindowEventManager.windowListener().focusGained());
    }

    @Test
    void clearAllListenersRemovesAllListenerTypes() {
        // Arrange
        int[] callCount = { 0 };

        WindowEventManager.registerWindowCreatedListener(
                window -> callCount[0]++);
        WindowEventManager.registerIconificationListener(
                value -> callCount[0]++);
        WindowEventManager.registerMaximizationListener(
                value -> callCount[0]++);
        WindowEventManager.registerFocusChangeListener(
                value -> callCount[0]++);
        WindowEventManager.registerCloseRequestListener(
                () -> {
                    callCount[0]++;
                    return false;
                });
        WindowEventManager.registerFilesDroppedListener(
                files -> callCount[0]++);
        WindowEventManager.registerWindowRefreshListener(
                () -> callCount[0]++);

        // Act
        WindowEventManager.clearAllListeners();

        WindowEventManager.windowListener().created(null);
        WindowEventManager.windowListener().iconified(true);
        WindowEventManager.windowListener().maximized(true);
        WindowEventManager.windowListener().focusGained();
        boolean closeAllowed = WindowEventManager.windowListener().closeRequested();
        WindowEventManager.windowListener().filesDropped(new String[] { "test.txt" });
        WindowEventManager.windowListener().refreshRequested();

        // Assert
        assertEquals(0, callCount[0]);
        assertTrue(closeAllowed);
    }

}