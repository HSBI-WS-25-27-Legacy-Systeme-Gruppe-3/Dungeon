package engine.systems.input;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.utils.Array;
import engine.Entity;
import engine.Game;
import engine.components.PlayerComponent;
import engine.input.CursorUtils;
import engine.network.handler.INetworkHandler;
import engine.network.messages.c2s.InputMessage;
import engine.utils.Direction;
import engine.utils.Point;
import feature.entities.HeroController;
import feature.input.configuration.JoystickConfig;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

class JoystickSystemTest {

  private JoystickSystem joystickSystem;

  private Controller controller;
  private Entity player;
  private PlayerComponent playerComponent;

  private Input input;
  private Graphics graphics;
  private InputProcessor inputProcessor;

  private MockedStatic<Controllers> controllersMock;
  private MockedStatic<Game> gameMock;
  private MockedStatic<MovementInputSystem> movementMock;
  private MockedStatic<CursorUtils> cursorMock;
  private MockedStatic<HeroController> heroMock;

  private final Point target = new Point(100, 200);

  @BeforeEach
  void setUp() {
    controller = mock(Controller.class);
    player = mock(Entity.class);
    playerComponent = mock(PlayerComponent.class);

    input = mock(Input.class);
    graphics = mock(Graphics.class);
    inputProcessor = mock(InputProcessor.class);

    Gdx.input = input;
    Gdx.graphics = graphics;

    when(input.getInputProcessor()).thenReturn(inputProcessor);
    when(input.getX()).thenReturn(100);
    when(input.getY()).thenReturn(100);
    when(graphics.getDeltaTime()).thenReturn(1f);

    controllersMock = mockStatic(Controllers.class);

    Array<Controller> controllers = new Array<>();
    controllers.add(controller);

    controllersMock.when(Controllers::getControllers).thenReturn(controllers);

    gameMock = mockStatic(Game.class);

    gameMock.when(() -> Game.levelEntities(anySet())).thenAnswer(invocation -> Stream.of(player));

    gameMock.when(Game::windowWidth).thenReturn(1920);
    gameMock.when(Game::windowHeight).thenReturn(1080);

    movementMock = mockStatic(MovementInputSystem.class);
    cursorMock = mockStatic(CursorUtils.class);
    heroMock = mockStatic(HeroController.class);

    cursorMock.when(CursorUtils::positionInWorld).thenReturn(target);

    joystickSystem = new JoystickSystem();

    when(player.fetch(PlayerComponent.class)).thenReturn(Optional.of(playerComponent));

    when(playerComponent.isLocal()).thenReturn(true);
  }

  @AfterEach
  void tearDown() {
    controllersMock.close();
    gameMock.close();
    movementMock.close();
    cursorMock.close();
    heroMock.close();

    Gdx.input = null;
    Gdx.graphics = null;
  }

  // =====================================================
  // Helper methods
  // =====================================================

  private void execute() {
    joystickSystem.execute();
  }

  private void neutralizeController() {
    when(controller.getAxis(anyInt())).thenReturn(0f);
    when(controller.getButton(anyInt())).thenReturn(false);
  }

  private INetworkHandler mockNetwork() {
    INetworkHandler network = mock(INetworkHandler.class);
    gameMock.when(Game::network).thenReturn(network);
    return network;
  }

  private void verifyInputSent(INetworkHandler network, InputMessage expected) {

    ArgumentCaptor<InputMessage> captor = ArgumentCaptor.forClass(InputMessage.class);

    verify(network).sendInput(captor.capture());

    InputMessage actual = captor.getValue();

    assertEquals(expected.action(), actual.action());
    assertEquals(expected.payload(), actual.payload());
  }

  private void verifyInputSentTimes(INetworkHandler network, InputMessage expected, int times) {

    ArgumentCaptor<InputMessage> captor = ArgumentCaptor.forClass(InputMessage.class);

    verify(network, times(times)).sendInput(captor.capture());

    for (InputMessage actual : captor.getAllValues()) {
      assertEquals(expected.action(), actual.action());
      assertEquals(expected.payload(), actual.payload());
    }
  }

  // =====================================================
  // Lifecycle tests
  // =====================================================

  @Test
  void stopShouldKeepSystemRunning() {
    joystickSystem.stop();
    assertTrue(joystickSystem.isRunning());
  }

  @Test
  void runShouldKeepSystemRunning() {
    joystickSystem.run();
    assertTrue(joystickSystem.isRunning());
  }

  // =====================================================
  // Movement tests
  // =====================================================

  @Test
  void shouldMoveUpWhenLeftStickMovesUp() {
    neutralizeController();

    when(controller.getAxis(JoystickConfig.AXIS_LEFT_Y)).thenReturn(-1f);

    execute();

    movementMock.verify(() -> MovementInputSystem.hold(Direction.UP));
  }

  @Test
  void shouldMoveDownWhenLeftStickMovesDown() {
    neutralizeController();

    when(controller.getAxis(JoystickConfig.AXIS_LEFT_Y)).thenReturn(1f);

    execute();

    movementMock.verify(() -> MovementInputSystem.hold(Direction.DOWN));
  }

  @Test
  void shouldMoveRightWhenLeftStickMovesRight() {
    neutralizeController();

    when(controller.getAxis(JoystickConfig.AXIS_LEFT_X)).thenReturn(1f);

    execute();

    movementMock.verify(() -> MovementInputSystem.hold(Direction.RIGHT));
  }

  @Test
  void shouldMoveLeftWhenLeftStickMovesLeft() {
    neutralizeController();

    when(controller.getAxis(JoystickConfig.AXIS_LEFT_X)).thenReturn(-1f);

    execute();

    movementMock.verify(() -> MovementInputSystem.hold(Direction.LEFT));
  }

  @Test
  void shouldUseDpadWhenLeftStickIsNeutral() {
    neutralizeController();

    when(controller.getButton(JoystickConfig.BUTTON_DPAD_UP)).thenReturn(true);

    when(controller.getButton(JoystickConfig.BUTTON_DPAD_RIGHT)).thenReturn(true);

    execute();

    movementMock.verify(() -> MovementInputSystem.hold(Direction.UP));

    movementMock.verify(() -> MovementInputSystem.hold(Direction.RIGHT));
  }

  @Test
  void shouldPreferLeftStickOverDpad() {
    neutralizeController();

    when(controller.getAxis(JoystickConfig.AXIS_LEFT_X)).thenReturn(1f);

    when(controller.getButton(JoystickConfig.BUTTON_DPAD_LEFT)).thenReturn(true);

    execute();

    movementMock.verify(() -> MovementInputSystem.hold(Direction.RIGHT));

    movementMock.verify(() -> MovementInputSystem.hold(Direction.LEFT), never());
  }

  @Test
  void shouldIgnoreMovementInsideDeadzone() {
    neutralizeController();

    when(controller.getAxis(JoystickConfig.AXIS_LEFT_X))
        .thenReturn(JoystickConfig.STICK_DEADZONE / 2f);

    when(controller.getAxis(JoystickConfig.AXIS_LEFT_Y))
        .thenReturn(JoystickConfig.STICK_DEADZONE / 2f);

    execute();

    movementMock.verifyNoInteractions();
  }

  @Test
  void shouldNotMoveWhenNoMovementInputExists() {
    neutralizeController();

    execute();

    movementMock.verifyNoInteractions();
  }

  // =====================================================
  // Skill action tests
  // =====================================================

  @Test
  void shouldCastSkillWhenRightTriggerIsPressed() {
    neutralizeController();

    INetworkHandler network = mockNetwork();

    when(controller.getAxis(JoystickConfig.RT_AXIS)).thenReturn(1f);

    execute();

    verifyInputSent(network, InputMessage.castSkill(target, true));
  }

  @Test
  void shouldNotCastSkillRepeatedlyWhileTriggerIsHeld() {
    neutralizeController();

    INetworkHandler network = mockNetwork();

    when(controller.getAxis(JoystickConfig.RT_AXIS)).thenReturn(1f);

    execute();
    execute();

    verifyInputSentTimes(network, InputMessage.castSkill(target, true), 1);
  }

  @Test
  void shouldCastSkillAgainAfterTriggerIsReleased() {
    neutralizeController();

    INetworkHandler network = mockNetwork();

    when(controller.getAxis(JoystickConfig.RT_AXIS)).thenReturn(1f);

    execute();

    when(controller.getAxis(JoystickConfig.RT_AXIS)).thenReturn(0f);

    execute();

    when(controller.getAxis(JoystickConfig.RT_AXIS)).thenReturn(1f);

    execute();

    verifyInputSentTimes(network, InputMessage.castSkill(target, true), 2);
  }

  @Test
  void shouldSwitchToNextSkillWhenButtonIsPressed() {
    neutralizeController();

    INetworkHandler network = mockNetwork();

    when(controller.getButton(JoystickConfig.BUTTON_NEXT_SKILL)).thenReturn(true);

    execute();

    verifyInputSent(network, InputMessage.nextSkill(true));
  }

  @Test
  void shouldSwitchToPreviousSkillWhenButtonIsPressed() {
    neutralizeController();

    INetworkHandler network = mockNetwork();

    when(controller.getButton(JoystickConfig.BUTTON_PREV_SKILL)).thenReturn(true);

    execute();

    verifyInputSent(network, InputMessage.prevSkill(true));
  }

  @Test
  void shouldNotRepeatButtonActionWhileButtonIsHeld() {
    neutralizeController();

    INetworkHandler network = mockNetwork();

    when(controller.getButton(JoystickConfig.BUTTON_NEXT_SKILL)).thenReturn(true);

    execute();
    execute();

    verifyInputSentTimes(network, InputMessage.nextSkill(true), 1);
  }

  @Test
  void shouldTriggerButtonActionAgainAfterRelease() {
    neutralizeController();

    INetworkHandler network = mockNetwork();

    when(controller.getButton(JoystickConfig.BUTTON_NEXT_SKILL)).thenReturn(true);

    execute();

    when(controller.getButton(JoystickConfig.BUTTON_NEXT_SKILL)).thenReturn(false);

    execute();

    when(controller.getButton(JoystickConfig.BUTTON_NEXT_SKILL)).thenReturn(true);

    execute();

    verifyInputSentTimes(network, InputMessage.nextSkill(true), 2);
  }

  // =====================================================
  // Interaction and inventory tests
  // =====================================================

  @Test
  void shouldRequestInteractionWhenInteractButtonIsPressed() {
    neutralizeController();

    when(controller.getButton(JoystickConfig.BUTTON_INTERACT)).thenReturn(true);

    gameMock.when(Game::player).thenReturn(Optional.of(player));

    execute();

    heroMock.verify(() -> HeroController.requestInteraction(player, target));
  }

  @Test
  void shouldNotRequestInteractionWhenNoPlayerExists() {
    neutralizeController();

    when(controller.getButton(JoystickConfig.BUTTON_INTERACT)).thenReturn(true);

    gameMock.when(Game::player).thenReturn(Optional.empty());

    execute();

    heroMock.verify(() -> HeroController.requestInteraction(any(), any()), never());
  }

  @Test
  void shouldToggleInventoryWhenInventoryButtonIsPressed() {
    neutralizeController();

    when(controller.getButton(JoystickConfig.BUTTON_INVENTAR)).thenReturn(true);

    execute();

    heroMock.verify(() -> HeroController.toggleInventory(player));
  }

  // =====================================================
  // Mouse simulation tests
  // =====================================================

  @Test
  void shouldSimulateMouseDownWhenLeftTriggerIsPressed() {
    neutralizeController();

    when(controller.getAxis(JoystickConfig.LT_AXIS)).thenReturn(1f);

    execute();

    verify(inputProcessor).touchDown(100, 100, 0, Input.Buttons.LEFT);
  }

  @Test
  void shouldSimulateMouseDraggedWhileLeftTriggerIsHeld() {
    neutralizeController();

    when(controller.getAxis(JoystickConfig.LT_AXIS)).thenReturn(1f);

    execute();
    execute();

    verify(inputProcessor).touchDragged(100, 100, 0);
  }

  @Test
  void shouldSimulateMouseUpWhenLeftTriggerIsReleased() {
    neutralizeController();

    when(controller.getAxis(JoystickConfig.LT_AXIS)).thenReturn(1f);

    execute();

    when(controller.getAxis(JoystickConfig.LT_AXIS)).thenReturn(0f);

    execute();

    verify(inputProcessor).touchUp(100, 100, 0, Input.Buttons.LEFT);
  }

  @Test
  void shouldNotSimulateMouseEventsWhenNoInputProcessorExists() {
    neutralizeController();

    when(input.getInputProcessor()).thenReturn(null);

    when(controller.getAxis(JoystickConfig.LT_AXIS)).thenReturn(1f);

    assertDoesNotThrow(this::execute);

    verifyNoInteractions(inputProcessor);
  }

  // =====================================================
  // Cursor movement tests
  // =====================================================

  @Test
  void shouldMoveCursorWhenRightStickIsUsed() {
    neutralizeController();

    when(controller.getAxis(JoystickConfig.AXIS_RIGHT_X)).thenReturn(1f);

    execute();

    verify(input).setCursorPosition(anyInt(), anyInt());
  }

  @Test
  void shouldNotMoveCursorWhenRightStickIsNeutral() {
    neutralizeController();

    execute();

    verify(input, never()).setCursorPosition(anyInt(), anyInt());
  }

  // =====================================================
  // Player filtering tests
  // =====================================================

  @Test
  void shouldNotProcessNonLocalPlayers() {
    neutralizeController();

    when(playerComponent.isLocal()).thenReturn(false);

    execute();

    verify(controller, never()).getAxis(anyInt());
    verify(controller, never()).getButton(anyInt());
  }

  @Test
  void shouldProcessLocalPlayers() {
    neutralizeController();

    when(playerComponent.isLocal()).thenReturn(true);

    when(controller.getAxis(JoystickConfig.AXIS_LEFT_X)).thenReturn(1f);

    execute();

    movementMock.verify(() -> MovementInputSystem.hold(Direction.RIGHT));
  }
}
