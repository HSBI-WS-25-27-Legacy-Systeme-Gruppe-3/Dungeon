package engine.systems.input;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import engine.Game;
import engine.network.handler.INetworkHandler;
import engine.network.messages.c2s.InputMessage;
import engine.utils.Vector2;
import java.lang.reflect.Field;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

class MovementInputSystemTest {

  private MovementInputSystem system;
  private INetworkHandler network;
  private MockedStatic<Game> gameMock;

  @BeforeEach
  void setUp() throws Exception {
    system = new MovementInputSystem();
    network = mock(INetworkHandler.class);

    gameMock = mockStatic(Game.class);
    gameMock.when(Game::network).thenReturn(network);

    setHeldInput(0, 0);
  }

  @AfterEach
  void tearDown() throws Exception {
    setHeldInput(0, 0);
    gameMock.close();
  }

  private void setHeldInput(float x, float y) throws Exception {
    Field heldX = MovementInputSystem.class.getDeclaredField("heldX");
    Field heldY = MovementInputSystem.class.getDeclaredField("heldY");

    heldX.setAccessible(true);
    heldY.setAccessible(true);

    heldX.setFloat(null, x);
    heldY.setFloat(null, y);
  }

  /**
   * Verifies that a movement message with the expected direction was sent.
   *
   * @param expectedDirection expected movement direction
   */
  private void verifyMovementInput(Vector2 expectedDirection) {
    ArgumentCaptor<InputMessage> captor = ArgumentCaptor.forClass(InputMessage.class);

    verify(network).sendInput(captor.capture());

    InputMessage actual = captor.getValue();

    assertEquals(InputMessage.Action.MOVE, actual.action());

    InputMessage.Move move = actual.payloadAs(InputMessage.Move.class);

    assertEquals(expectedDirection.x(), move.direction().x());
    assertEquals(expectedDirection.y(), move.direction().y());
  }

  /** Verifies that a zero-direction stop message was sent over both transports. */
  private void verifyStopMessage() {
    ArgumentCaptor<InputMessage> captor = ArgumentCaptor.forClass(InputMessage.class);

    verify(network).sendInput(captor.capture());

    InputMessage actual = captor.getValue();

    assertEquals(InputMessage.Action.MOVE, actual.action());

    InputMessage.Move move = actual.payloadAs(InputMessage.Move.class);

    assertTrue(move.direction().isZero());

    verify(network).send(eq((short) 0), eq(actual), eq(true));
  }

  @Test
  void executeShouldSendMovementInputWhenDirectionIsHeld() throws Exception {
    setHeldInput(0, 1);

    system.execute();

    verifyMovementInput(Vector2.of(0, 1));
  }

  @Test
  void executeShouldCombineMultipleHeldDirections() throws Exception {
    setHeldInput(1, 1);

    system.execute();

    verifyMovementInput(Vector2.of(1, 1));
  }

  @Test
  void executeShouldNormalizeMultipleIdenticalDirections() throws Exception {
    setHeldInput(2, 0);

    system.execute();

    verifyMovementInput(Vector2.of(1, 0));
  }

  @Test
  void executeShouldResetHeldInputAfterExecution() throws Exception {
    setHeldInput(0, 1);

    system.execute();
    system.execute();

    ArgumentCaptor<InputMessage> captor = ArgumentCaptor.forClass(InputMessage.class);

    verify(network, times(2)).sendInput(captor.capture());

    var messages = captor.getAllValues();

    // First execution sends movement.
    assertEquals(InputMessage.Action.MOVE, messages.getFirst().action());

    Vector2 movement = messages.getFirst().payloadAs(InputMessage.Move.class).direction();

    assertEquals(0f, movement.x());
    assertEquals(1f, movement.y());

    // Second execution sends stop.
    Vector2 stop = messages.get(1).payloadAs(InputMessage.Move.class).direction();

    assertTrue(stop.isZero());

    // Stop must also be sent reliably over TCP.
    verify(network).send(eq((short) 0), eq(messages.get(1)), eq(true));
  }

  @Test
  void executeShouldSendStopWhenMovementEnds() throws Exception {
    setHeldInput(0, 1);
    system.execute();

    clearInvocations(network);

    system.execute();

    verifyStopMessage();
  }

  @Test
  void executeShouldNotSendStopIfPlayerNeverMoved() {
    system.execute();

    verifyNoInteractions(network);
  }

  @Test
  void executeShouldNotSendRepeatedStopMessages() throws Exception {
    setHeldInput(0, 1);
    system.execute();

    clearInvocations(network);

    system.execute();
    system.execute();

    verifyStopMessage();
    verifyNoMoreInteractions(network);
  }

  @Test
  void executeShouldResumeMovementAfterStop() throws Exception {
    setHeldInput(0, 1);
    system.execute();

    system.execute();

    clearInvocations(network);

    setHeldInput(1, 0);
    system.execute();

    verifyMovementInput(Vector2.of(1, 0));
  }

  @Test
  void stopShouldKeepSystemRunning() throws Exception {
    setHeldInput(0, 1);

    system.stop();
    system.execute();

    verifyMovementInput(Vector2.of(0, 1));
  }
}
