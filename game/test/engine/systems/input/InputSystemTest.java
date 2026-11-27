package engine.systems.input;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.badlogic.gdx.Input;
import engine.Entity;
import engine.Game;
import engine.components.InputComponent;
import feature.systems.HudSystem;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class InputSystemTest {

  private InputSystem system;
  private Entity entity;
  private InputComponent component;
  private HudSystem hud;
  private Consumer<Entity> callback;

  private MockedStatic<Game> gameMock;
  private MockedStatic<InputManager> inputMock;

  @BeforeEach
  void setUp() {
    entity = mock(Entity.class);
    component = mock(InputComponent.class);
    hud = mock(HudSystem.class);
    callback = mock(Consumer.class);

    gameMock = mockStatic(Game.class);
    inputMock = mockStatic(InputManager.class);

    gameMock.when(Game::hud).thenReturn(hud);

    when(entity.fetch(InputComponent.class)).thenReturn(Optional.of(component));

    when(component.deactivateControls()).thenReturn(false);
    when(component.callbacks()).thenReturn(new HashMap<>());

    system = spy(new InputSystem());

    doReturn(Stream.of(entity)).when(system).filteredEntityStream(InputComponent.class);
  }

  @AfterEach
  void tearDown() {
    inputMock.close();
    gameMock.close();
  }

  private InputComponent.InputData inputData(boolean pauseable, boolean repeat) {

    InputComponent.InputData data = mock(InputComponent.InputData.class);

    when(data.pauseable()).thenReturn(pauseable);
    when(data.repeat()).thenReturn(repeat);
    when(data.callback()).thenReturn(callback);

    return data;
  }

  private void register(int key, boolean pauseable, boolean repeat) {
    Map<Integer, InputComponent.InputData> callbacks = new HashMap<>();

    callbacks.put(key, inputData(pauseable, repeat));

    when(component.callbacks()).thenReturn(callbacks);
  }

  private void pressKey(int key) {
    inputMock.when(() -> InputManager.isKeyJustPressed(key)).thenReturn(true);
  }

  private void holdKey(int key) {
    inputMock.when(() -> InputManager.isKeyPressed(key)).thenReturn(true);
  }

  private void pressButton(int button) {
    inputMock.when(() -> InputManager.isButtonJustPressed(button)).thenReturn(true);
  }

  private void holdButton(int button) {
    inputMock.when(() -> InputManager.isButtonPressed(button)).thenReturn(true);
  }

  // ---------------------------------------------------------
  // System state
  // ---------------------------------------------------------

  @Test
  void shouldStartPaused() {
    register(Input.Keys.SPACE, false, false);
    pressKey(Input.Keys.SPACE);

    system.execute();

    verify(callback).accept(entity);
  }

  @Test
  void shouldExecuteCallbacksWhenRunning() {
    system.run();

    register(Input.Keys.SPACE, false, false);
    pressKey(Input.Keys.SPACE);

    system.execute();

    verify(callback).accept(entity);
  }

  @Test
  void shouldNotExecuteNonPauseableCallbacksAfterStop() {
    system.run();
    system.stop();

    register(Input.Keys.SPACE, false, false);
    pressKey(Input.Keys.SPACE);

    system.execute();

    verify(callback, never()).accept(any());
  }

  @Test
  void shouldAlwaysKeepSystemRunningAfterStop() {
    system.stop();

    // stop() must not disable the system.
    assertDoesNotThrow(system::execute);
  }

  // ---------------------------------------------------------
  // Pauseable callbacks
  // ---------------------------------------------------------

  @Test
  void shouldExecutePauseableCallbacksWhilePaused() {
    register(Input.Keys.ESCAPE, true, false);
    pressKey(Input.Keys.ESCAPE);

    system.execute();

    verify(callback).accept(entity);
  }

  @Test
  void shouldExecuteNonPauseableCallbacksAfterStop() {
    register(Input.Keys.SPACE, false, false);
    pressKey(Input.Keys.SPACE);

    system.stop();
    system.execute();

    verify(callback, never()).accept(any());
  }

  // ---------------------------------------------------------
  // Control deactivation
  // ---------------------------------------------------------

  @Test
  void shouldSkipAllCallbacksWhenControlsAreDeactivatedAndNoPausingUIIsOpen() {
    when(component.deactivateControls()).thenReturn(true);
    when(hud.hasOpenPausingUI(entity)).thenReturn(false);

    register(Input.Keys.ESCAPE, true, false);
    pressKey(Input.Keys.ESCAPE);

    system.execute();

    verify(callback, never()).accept(any());
    inputMock.verifyNoInteractions();
  }

  @Test
  void shouldAllowPauseableCallbacksWhenControlsAreDeactivatedButPausingUIIsOpen() {
    when(component.deactivateControls()).thenReturn(true);
    when(hud.hasOpenPausingUI(entity)).thenReturn(true);

    register(Input.Keys.ESCAPE, true, false);
    pressKey(Input.Keys.ESCAPE);

    system.execute();

    verify(callback).accept(entity);
  }

  @Test
  void shouldBlockNonPauseableCallbacksWhenControlsAreDeactivated() {
    when(component.deactivateControls()).thenReturn(true);
    when(hud.hasOpenPausingUI(entity)).thenReturn(true);

    register(Input.Keys.SPACE, false, false);
    pressKey(Input.Keys.SPACE);

    system.execute();

    verify(callback, never()).accept(any());
  }

  // ---------------------------------------------------------
  // HUD gameplay input
  // ---------------------------------------------------------

  @Test
  void shouldBlockGameplayCallbacksWhenHudBlocksInput() {
    system.run();

    when(hud.blocksGameplayInput(entity)).thenReturn(true);

    register(Input.Keys.SPACE, false, false);
    pressKey(Input.Keys.SPACE);

    system.execute();

    verify(callback, never()).accept(any());
  }

  @Test
  void shouldAllowPauseableCallbacksWhenHudBlocksGameplayInput() {
    system.run();

    when(hud.blocksGameplayInput(entity)).thenReturn(true);

    register(Input.Keys.ESCAPE, true, false);
    pressKey(Input.Keys.ESCAPE);

    system.execute();

    verify(callback).accept(entity);
  }

  @Test
  void shouldAllowGameplayCallbacksWhenHudDoesNotBlockInput() {
    system.run();

    when(hud.blocksGameplayInput(entity)).thenReturn(false);

    register(Input.Keys.SPACE, false, false);
    pressKey(Input.Keys.SPACE);

    system.execute();

    verify(callback).accept(entity);
  }

  // ---------------------------------------------------------
  // Keyboard input and repeat
  // ---------------------------------------------------------

  @Test
  void shouldExecuteCallbackWhenKeyIsJustPressedWithoutRepeat() {
    system.run();

    register(Input.Keys.SPACE, false, false);
    pressKey(Input.Keys.SPACE);

    system.execute();

    verify(callback).accept(entity);
  }

  @Test
  void shouldNotExecuteCallbackWhenKeyIsHeldWithoutRepeat() {
    system.run();

    register(Input.Keys.SPACE, false, false);
    holdKey(Input.Keys.SPACE);

    system.execute();

    verify(callback, never()).accept(any());
  }

  @Test
  void shouldExecuteCallbackWhenKeyIsHeldWithRepeat() {
    system.run();

    register(Input.Keys.SPACE, false, true);
    holdKey(Input.Keys.SPACE);

    system.execute();

    verify(callback).accept(entity);
  }

  @Test
  void shouldNotExecuteCallbackWhenKeyIsReleasedWithRepeat() {
    system.run();

    register(Input.Keys.SPACE, false, true);

    system.execute();

    verify(callback, never()).accept(any());
  }

  @Test
  void shouldExecuteCallbackWhenKeyIsJustPressedEvenWithRepeat() {
    system.run();

    register(Input.Keys.SPACE, false, true);
    pressKey(Input.Keys.SPACE);
    holdKey(Input.Keys.SPACE);

    system.execute();

    verify(callback).accept(entity);
  }

  // ---------------------------------------------------------
  // Mouse input
  // ---------------------------------------------------------

  @Test
  void shouldUseMouseInputForLeftButton() {
    system.run();

    register(Input.Buttons.LEFT, false, false);
    pressButton(Input.Buttons.LEFT);

    system.execute();

    verify(callback).accept(entity);

    inputMock.verify(() -> InputManager.isButtonJustPressed(Input.Buttons.LEFT));

    inputMock.verify(() -> InputManager.isKeyJustPressed(Input.Buttons.LEFT), never());
  }

  @Test
  void shouldUseMouseInputForRightButton() {
    system.run();

    register(Input.Buttons.RIGHT, false, false);
    pressButton(Input.Buttons.RIGHT);

    system.execute();

    verify(callback).accept(entity);
  }

  @Test
  void shouldUseMouseInputForMiddleButton() {
    system.run();

    register(Input.Buttons.MIDDLE, false, false);
    pressButton(Input.Buttons.MIDDLE);

    system.execute();

    verify(callback).accept(entity);
  }

  @Test
  void shouldSupportMouseButtonRepeat() {
    system.run();

    register(Input.Buttons.LEFT, false, true);
    holdButton(Input.Buttons.LEFT);

    system.execute();

    verify(callback).accept(entity);
  }

  // ---------------------------------------------------------
  // Entity handling
  // ---------------------------------------------------------

  @Test
  void shouldExecuteCallbacksForCorrectEntity() {
    system.run();

    Entity anotherEntity = mock(Entity.class);
    InputComponent anotherComponent = mock(InputComponent.class);

    when(anotherEntity.fetch(InputComponent.class)).thenReturn(Optional.of(anotherComponent));

    when(anotherComponent.deactivateControls()).thenReturn(false);

    Consumer<Entity> anotherCallback = mock(Consumer.class);

    InputComponent.InputData data = mock(InputComponent.InputData.class);

    when(data.pauseable()).thenReturn(false);
    when(data.repeat()).thenReturn(false);
    when(data.callback()).thenReturn(anotherCallback);

    when(anotherComponent.callbacks()).thenReturn(Map.of(Input.Keys.SPACE, data));

    doReturn(Stream.of(entity, anotherEntity))
        .when(system)
        .filteredEntityStream(InputComponent.class);

    register(Input.Keys.SPACE, false, false);
    pressKey(Input.Keys.SPACE);

    system.execute();

    verify(callback).accept(entity);
    verify(anotherCallback).accept(anotherEntity);
  }
}
