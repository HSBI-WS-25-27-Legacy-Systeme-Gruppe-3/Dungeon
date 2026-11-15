package engine.network.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

public class AuthoritativeServerLoopTest {

  private ServerTransport serverTransport;
  private AuthoritativeServerLoop serverLoop;

  @BeforeEach
  public void setUp() {
    serverTransport = mock(ServerTransport.class);
    serverLoop = new AuthoritativeServerLoop(serverTransport);
    serverLoop.start();
  }

  @AfterEach
  void tearDown() {
    serverLoop.stop();
  }

  @Test
  void stopShutsDownExecutor() {
    assertTrue(serverLoop.isRunning());
    serverLoop.stop();
    assertFalse(serverLoop.isRunning());
  }



// TODO: Verify that no client baselines are reset when the first level is detected.

// TODO: Verify that each subsequent level change clears the snapshot history
//  and resets the baselines of all connected clients.

// TODO: Verify that client baselines and snapshot history are not reset when the level is unchanged.

// TODO: Verify that a new level object is detected as a level change, even if its name is unchanged.
}
