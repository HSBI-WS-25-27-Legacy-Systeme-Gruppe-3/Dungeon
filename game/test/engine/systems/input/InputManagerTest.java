package engine.systems.input;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import engine.Game;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class InputManagerTest {

  private Input input;
  private InputProcessor processor;
  private MockedStatic<Game> mockedGame;

  @BeforeEach
  void setUp() {
    input = mock(Input.class);
    processor = mock(InputProcessor.class);

    // Directly assign the mocked libGDX input.
    Gdx.input = input;

    mockedGame = mockStatic(Game.class);
    mockedGame.when(Game::stage).thenReturn(Optional.empty());

    when(input.getInputProcessor()).thenReturn(processor);

    InputManager.reset();
    InputManager.init();
  }

  @AfterEach
  void tearDown() {
    InputManager.reset();

    Gdx.input = null;

    mockedGame.close();
  }

  private InputProcessor captureWrapper() {
    org.mockito.ArgumentCaptor<InputProcessor> captor =
        org.mockito.ArgumentCaptor.forClass(InputProcessor.class);

    verify(input, atLeastOnce()).setInputProcessor(captor.capture());

    return captor.getValue();
  }

  // ---------------------------------------------------------
  // Key press and release tests
  // ---------------------------------------------------------

  @Test
  void keyPressShouldBeTracked() {
    InputProcessor wrapper = captureWrapper();

    wrapper.keyDown(Input.Keys.SPACE);

    assertTrue(InputManager.isKeyJustPressed(Input.Keys.SPACE));
    assertTrue(InputManager.isKeyPressed(Input.Keys.SPACE));
    assertFalse(InputManager.isKeyJustReleased(Input.Keys.SPACE));
  }

  @Test
  void keyPressShouldTransitionToPressedAfterUpdate() {
    InputProcessor wrapper = captureWrapper();

    wrapper.keyDown(Input.Keys.SPACE);
    InputManager.update();

    assertFalse(InputManager.isKeyJustPressed(Input.Keys.SPACE));
    assertTrue(InputManager.isKeyPressed(Input.Keys.SPACE));
  }

  @Test
  void keyReleaseShouldBeTracked() {
    InputProcessor wrapper = captureWrapper();

    wrapper.keyDown(Input.Keys.SPACE);
    InputManager.update();

    wrapper.keyUp(Input.Keys.SPACE);

    assertFalse(InputManager.isKeyPressed(Input.Keys.SPACE));
    assertTrue(InputManager.isKeyJustReleased(Input.Keys.SPACE));
  }

  @Test
  void releaseStateShouldClearAfterUpdate() {
    InputProcessor wrapper = captureWrapper();

    wrapper.keyDown(Input.Keys.SPACE);
    InputManager.update();

    wrapper.keyUp(Input.Keys.SPACE);

    assertTrue(InputManager.isKeyJustReleased(Input.Keys.SPACE));

    InputManager.update();

    assertFalse(InputManager.isKeyJustReleased(Input.Keys.SPACE));
    assertFalse(InputManager.isKeyPressed(Input.Keys.SPACE));
  }

  // ---------------------------------------------------------
  // Mouse button tests
  // ---------------------------------------------------------

  @Test
  void mouseButtonPressShouldBeTracked() {
    InputProcessor wrapper = captureWrapper();

    wrapper.touchDown(100, 100, 0, Input.Buttons.LEFT);

    assertTrue(InputManager.isButtonJustPressed(Input.Buttons.LEFT));
    assertTrue(InputManager.isButtonPressed(Input.Buttons.LEFT));
  }

  @Test
  void mouseButtonReleaseShouldBeTracked() {
    InputProcessor wrapper = captureWrapper();

    wrapper.touchDown(100, 100, 0, Input.Buttons.LEFT);
    InputManager.update();

    wrapper.touchUp(100, 100, 0, Input.Buttons.LEFT);

    assertFalse(InputManager.isButtonPressed(Input.Buttons.LEFT));
    assertTrue(InputManager.isButtonJustReleased(Input.Buttons.LEFT));
  }

  @Test
  void touchCancelledShouldReleaseButton() {
    InputProcessor wrapper = captureWrapper();

    wrapper.touchDown(100, 100, 0, Input.Buttons.LEFT);
    InputManager.update();

    wrapper.touchCancelled(100, 100, 0, Input.Buttons.LEFT);

    assertFalse(InputManager.isButtonPressed(Input.Buttons.LEFT));
    assertTrue(InputManager.isButtonJustReleased(Input.Buttons.LEFT));
  }

  // ---------------------------------------------------------
  // Keyboard focus tests
  // ---------------------------------------------------------

  @Test
  void keyboardFocusShouldFilterRegularKeyPresses() {
    Stage stage = mock(Stage.class);

    mockedGame.when(Game::stage).thenReturn(Optional.of(stage));
    when(stage.getKeyboardFocus()).thenReturn(mock(com.badlogic.gdx.scenes.scene2d.Actor.class));

    InputManager.reset();
    InputManager.init();

    InputProcessor wrapper = captureWrapper();

    wrapper.keyDown(Input.Keys.A);

    assertFalse(InputManager.isKeyJustPressed(Input.Keys.A));
    assertTrue(InputManager.isKeyJustPressed(Input.Keys.A, true));
  }

  @Test
  void escapeShouldWorkEvenWhenKeyboardFocusIsActive() {
    Stage stage = mock(Stage.class);

    mockedGame.when(Game::stage).thenReturn(Optional.of(stage));
    when(stage.getKeyboardFocus()).thenReturn(mock(com.badlogic.gdx.scenes.scene2d.Actor.class));

    InputManager.reset();
    InputManager.init();

    InputProcessor wrapper = captureWrapper();

    wrapper.keyDown(Input.Keys.ESCAPE);

    assertTrue(InputManager.isKeyJustPressed(Input.Keys.ESCAPE));
  }

  @Test
  void updateShouldClearUnfilteredJustPressedKeys() {
    InputProcessor wrapper = captureWrapper();

    wrapper.keyDown(Input.Keys.A);

    assertTrue(InputManager.isKeyJustPressed(Input.Keys.A, true));

    InputManager.update();

    assertFalse(InputManager.isKeyJustPressed(Input.Keys.A, true));
  }

  // ---------------------------------------------------------
  // Double-tap tests
  // ---------------------------------------------------------

  @Test
  void doubleTapShouldNotBeDetectedOnFirstPress() {
    InputProcessor wrapper = captureWrapper();

    wrapper.keyDown(Input.Keys.A);

    assertFalse(InputManager.isKeyDoubleTapped(Input.Keys.A));
  }

  @Test
  void doubleTapShouldBeDetectedWithinDefaultInterval() throws InterruptedException {

    InputProcessor wrapper = captureWrapper();

    wrapper.keyDown(Input.Keys.A);
    wrapper.keyUp(Input.Keys.A);

    InputManager.update();

    Thread.sleep(50);

    wrapper.keyDown(Input.Keys.A);

    assertTrue(InputManager.isKeyDoubleTapped(Input.Keys.A));
  }

  @Test
  void doubleTapShouldRespectCustomInterval() throws InterruptedException {

    InputProcessor wrapper = captureWrapper();

    wrapper.keyDown(Input.Keys.A);
    wrapper.keyUp(Input.Keys.A);

    InputManager.update();

    Thread.sleep(50);

    wrapper.keyDown(Input.Keys.A);

    assertTrue(InputManager.isKeyDoubleTapped(Input.Keys.A, 300));
    assertFalse(InputManager.isKeyDoubleTapped(Input.Keys.A, 10));
  }

  @Test
  void negativeDoubleTapIntervalShouldReturnFalse() {
    InputProcessor wrapper = captureWrapper();

    wrapper.keyDown(Input.Keys.A);

    assertFalse(InputManager.isKeyDoubleTapped(Input.Keys.A, -1));
  }

  @Test
  void buttonDoubleTapShouldBeDetected() throws InterruptedException {

    InputProcessor wrapper = captureWrapper();

    wrapper.touchDown(100, 100, 0, Input.Buttons.LEFT);
    wrapper.touchUp(100, 100, 0, Input.Buttons.LEFT);

    InputManager.update();

    Thread.sleep(50);

    wrapper.touchDown(100, 100, 0, Input.Buttons.LEFT);

    assertTrue(InputManager.isButtonDoubleTapped(Input.Buttons.LEFT));
  }

  // ---------------------------------------------------------
  // Hold-duration tests
  // ---------------------------------------------------------

  @Test
  void heldKeyShouldReturnFalseBeforeDuration() {
    InputProcessor wrapper = captureWrapper();

    wrapper.keyDown(Input.Keys.A);

    assertFalse(InputManager.isKeyHeld(Input.Keys.A, 1000));
  }

  @Test
  void nonPositiveHoldDurationShouldBehaveLikePressed() {
    InputProcessor wrapper = captureWrapper();

    wrapper.keyDown(Input.Keys.A);

    assertTrue(InputManager.isKeyHeld(Input.Keys.A, 0));
    assertTrue(InputManager.isKeyHeld(Input.Keys.A, -1));
  }

  @Test
  void releasedKeyShouldNotBeHeld() {
    InputProcessor wrapper = captureWrapper();

    wrapper.keyDown(Input.Keys.A);
    InputManager.update();

    wrapper.keyUp(Input.Keys.A);

    assertFalse(InputManager.isKeyHeld(Input.Keys.A, 0));
    assertFalse(InputManager.isKeyHeld(Input.Keys.A, 100));
  }

  @Test
  void nonPositiveButtonHoldDurationShouldBehaveLikePressed() {
    InputProcessor wrapper = captureWrapper();

    wrapper.touchDown(100, 100, 0, Input.Buttons.LEFT);

    assertTrue(InputManager.isButtonHeld(Input.Buttons.LEFT, 0));
  }

  // ---------------------------------------------------------
  // Scroll tests
  // ---------------------------------------------------------

  @Test
  void scrollAmountShouldAccumulateDuringFrame() {
    InputProcessor wrapper = captureWrapper();

    wrapper.scrolled(0, 2);
    wrapper.scrolled(0, 3);

    assertEquals(5f, InputManager.scrollAmountY());
  }

  @Test
  void updateShouldClearScrollAmount() {
    InputProcessor wrapper = captureWrapper();

    wrapper.scrolled(0, 5);

    assertEquals(5f, InputManager.scrollAmountY());

    InputManager.update();

    assertEquals(0f, InputManager.scrollAmountY());
  }

  // ---------------------------------------------------------
  // Reset tests
  // ---------------------------------------------------------

  @Test
  void resetShouldClearAllInputStates() {
    InputProcessor wrapper = captureWrapper();

    wrapper.keyDown(Input.Keys.SPACE);
    wrapper.touchDown(100, 100, 0, Input.Buttons.LEFT);
    wrapper.scrolled(0, 5);

    InputManager.reset();

    assertFalse(InputManager.isKeyPressed(Input.Keys.SPACE));
    assertFalse(InputManager.isKeyJustPressed(Input.Keys.SPACE));
    assertFalse(InputManager.isKeyJustReleased(Input.Keys.SPACE));

    assertFalse(InputManager.isButtonPressed(Input.Buttons.LEFT));
    assertFalse(InputManager.isButtonJustPressed(Input.Buttons.LEFT));
    assertFalse(InputManager.isButtonJustReleased(Input.Buttons.LEFT));

    assertEquals(0f, InputManager.scrollAmountY());
  }

  // ---------------------------------------------------------
  // Input processor delegation tests
  // ---------------------------------------------------------

  @Test
  void eventsShouldBeForwardedToPreviousProcessor() {
    InputProcessor wrapper = captureWrapper();

    when(processor.keyDown(Input.Keys.A)).thenReturn(true);
    when(processor.keyUp(Input.Keys.A)).thenReturn(true);
    when(processor.touchDown(10, 20, 0, Input.Buttons.LEFT)).thenReturn(true);

    assertTrue(wrapper.keyDown(Input.Keys.A));
    assertTrue(wrapper.keyUp(Input.Keys.A));
    assertTrue(wrapper.touchDown(10, 20, 0, Input.Buttons.LEFT));

    verify(processor).keyDown(Input.Keys.A);
    verify(processor).keyUp(Input.Keys.A);
    verify(processor).touchDown(10, 20, 0, Input.Buttons.LEFT);
  }

  @Test
  void eventsShouldReturnFalseWhenPreviousProcessorReturnsFalse() {
    InputProcessor wrapper = captureWrapper();

    when(processor.keyDown(Input.Keys.A)).thenReturn(false);

    assertFalse(wrapper.keyDown(Input.Keys.A));
  }

  @Test
  void repeatedKeyDownShouldNotResetHoldStartTime() {
    InputProcessor wrapper = captureWrapper();

    wrapper.keyDown(Input.Keys.A);
    wrapper.keyDown(Input.Keys.A);

    assertTrue(InputManager.isKeyPressed(Input.Keys.A));
  }
}
