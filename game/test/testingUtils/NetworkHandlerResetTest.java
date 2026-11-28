package testingUtils;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

import engine.Game;
import engine.network.handler.INetworkHandler;
import engine.network.handler.LocalNetworkHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NetworkHandlerResetTest {

  private INetworkHandler original;
  private final NetworkHandlerReset reset = new NetworkHandlerReset();

  @BeforeEach
  void setUp() {
    original = MockNetworkHandler.current();
  }

  @AfterEach
  void tearDown() {
    MockNetworkHandler.useNetworkHandler(original);
  }

  @Test
  void setsALocalHandlerBeforeTheTest() {
    INetworkHandler other = mock(INetworkHandler.class);
    MockNetworkHandler.useNetworkHandler(other);

    reset.before();

    assertInstanceOf(LocalNetworkHandler.class, Game.network());
    assertNotSame(other, Game.network());
  }

  @Test
  void everyTestGetsItsOwnLocalHandler() {
    reset.before();
    INetworkHandler first = Game.network();
    reset.after();

    reset.before();

    assertNotSame(first, Game.network());
  }

  @Test
  void restoresThePreviousHandlerAfterTheTest() {
    INetworkHandler other = mock(INetworkHandler.class);
    MockNetworkHandler.useNetworkHandler(other);

    reset.before();
    reset.after();

    assertSame(other, MockNetworkHandler.current());
  }

  @Test
  void restoresAMissingHandler() {
    MockNetworkHandler.useNetworkHandler(null);

    reset.before();
    reset.after();

    assertNull(MockNetworkHandler.current());
  }
}
