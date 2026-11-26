package engine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

class ManagedServerStatusTest {

  @Test
  void unmanagedServerDoesNotSendStatus() {
    String previousValue = System.getProperty(ServerProcess.MANAGED_PROPERTY);

    try {
      System.clearProperty(ServerProcess.MANAGED_PROPERTY);

      assertFalse(ManagedServerStatus.send("Server stopped"));
    } finally {
      if (previousValue == null) {
        System.clearProperty(ServerProcess.MANAGED_PROPERTY);
      } else {
        System.setProperty(ServerProcess.MANAGED_PROPERTY, previousValue);
      }
    }
  }

  @Test
  void managedServerWithoutStatusPortDoesNotSendStatus() {
    String previousManaged = System.getProperty(ServerProcess.MANAGED_PROPERTY);
    String previousPort = System.getProperty(ServerProcess.STATUS_PORT_PROPERTY);

    try {
      System.setProperty(ServerProcess.MANAGED_PROPERTY, "true");
      System.clearProperty(ServerProcess.STATUS_PORT_PROPERTY);

      assertFalse(ManagedServerStatus.send("Server stopped"));
    } finally {
      if (previousManaged == null) {
        System.clearProperty(ServerProcess.MANAGED_PROPERTY);
      } else {
        System.setProperty(ServerProcess.MANAGED_PROPERTY, previousManaged);
      }

      if (previousPort == null) {
        System.clearProperty(ServerProcess.STATUS_PORT_PROPERTY);
      } else {
        System.setProperty(ServerProcess.STATUS_PORT_PROPERTY, previousPort);
      }
    }
  }

  @Test
  void invalidStatusPortIsHandled() {
    String previousManaged = System.getProperty(ServerProcess.MANAGED_PROPERTY);
    String previousPort = System.getProperty(ServerProcess.STATUS_PORT_PROPERTY);

    try {
      System.setProperty(ServerProcess.MANAGED_PROPERTY, "true");
      System.setProperty(ServerProcess.STATUS_PORT_PROPERTY, "invalid");

      assertFalse(ManagedServerStatus.send("Server stopped"));
    } finally {
      if (previousManaged == null) {
        System.clearProperty(ServerProcess.MANAGED_PROPERTY);
      } else {
        System.setProperty(ServerProcess.MANAGED_PROPERTY, previousManaged);
      }

      if (previousPort == null) {
        System.clearProperty(ServerProcess.STATUS_PORT_PROPERTY);
      } else {
        System.setProperty(ServerProcess.STATUS_PORT_PROPERTY, previousPort);
      }
    }
  }

  @Test
  void connectionFailureIsHandled() {
    String previousManaged = System.getProperty(ServerProcess.MANAGED_PROPERTY);
    String previousPort = System.getProperty(ServerProcess.STATUS_PORT_PROPERTY);

    try (MockedConstruction<Socket> sockets =
        mockConstruction(
            Socket.class,
            (socket, context) ->
                doThrow(new IOException("Connection refused"))
                    .when(socket)
                    .connect(any(SocketAddress.class), anyInt()))) {

      System.setProperty(ServerProcess.MANAGED_PROPERTY, "true");
      System.setProperty(ServerProcess.STATUS_PORT_PROPERTY, "12345");

      assertFalse(ManagedServerStatus.send("Server stopped"));

      assertEquals(1, sockets.constructed().size());
    } finally {
      if (previousManaged == null) {
        System.clearProperty(ServerProcess.MANAGED_PROPERTY);
      } else {
        System.setProperty(ServerProcess.MANAGED_PROPERTY, previousManaged);
      }

      if (previousPort == null) {
        System.clearProperty(ServerProcess.STATUS_PORT_PROPERTY);
      } else {
        System.setProperty(ServerProcess.STATUS_PORT_PROPERTY, previousPort);
      }
    }
  }

  @Test
  void statusIsSentSuccessfully() throws IOException {
    String previousManaged = System.getProperty(ServerProcess.MANAGED_PROPERTY);
    String previousPort = System.getProperty(ServerProcess.STATUS_PORT_PROPERTY);

    ByteArrayOutputStream sentBytes = new ByteArrayOutputStream();

    try (MockedConstruction<Socket> sockets =
        mockConstruction(
            Socket.class,
            (socket, context) -> when(socket.getOutputStream()).thenReturn(sentBytes))) {

      System.setProperty(ServerProcess.MANAGED_PROPERTY, "true");
      System.setProperty(ServerProcess.STATUS_PORT_PROPERTY, "12345");

      assertTrue(ManagedServerStatus.send("Server ready"));

      assertEquals(1, sockets.constructed().size());

      try (DataInputStream input =
          new DataInputStream(new ByteArrayInputStream(sentBytes.toByteArray()))) {
        assertEquals("Server ready", input.readUTF());
      }
    } finally {
      if (previousManaged == null) {
        System.clearProperty(ServerProcess.MANAGED_PROPERTY);
      } else {
        System.setProperty(ServerProcess.MANAGED_PROPERTY, previousManaged);
      }

      if (previousPort == null) {
        System.clearProperty(ServerProcess.STATUS_PORT_PROPERTY);
      } else {
        System.setProperty(ServerProcess.STATUS_PORT_PROPERTY, previousPort);
      }
    }
  }

  @Test
  void nullPayloadIsHandled() {
    String previousManaged = System.getProperty(ServerProcess.MANAGED_PROPERTY);
    String previousPort = System.getProperty(ServerProcess.STATUS_PORT_PROPERTY);

    try (MockedConstruction<Socket> sockets =
        mockConstruction(
            Socket.class,
            (socket, context) ->
                when(socket.getOutputStream()).thenReturn(new ByteArrayOutputStream()))) {

      System.setProperty(ServerProcess.MANAGED_PROPERTY, "true");
      System.setProperty(ServerProcess.STATUS_PORT_PROPERTY, "12345");

      assertFalse(ManagedServerStatus.send(null));

      assertEquals(1, sockets.constructed().size());
    } finally {
      if (previousManaged == null) {
        System.clearProperty(ServerProcess.MANAGED_PROPERTY);
      } else {
        System.setProperty(ServerProcess.MANAGED_PROPERTY, previousManaged);
      }

      if (previousPort == null) {
        System.clearProperty(ServerProcess.STATUS_PORT_PROPERTY);
      } else {
        System.setProperty(ServerProcess.STATUS_PORT_PROPERTY, previousPort);
      }
    }
  }
}
