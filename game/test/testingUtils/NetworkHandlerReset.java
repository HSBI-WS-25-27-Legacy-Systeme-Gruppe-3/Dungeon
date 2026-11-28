package testingUtils;

import engine.network.handler.INetworkHandler;

/** Setzt vor jedem Test einen lokalen Netzwerk-Handler und stellt danach den alten wieder her. */
public final class NetworkHandlerReset {

  private INetworkHandler previous;

  /** Merkt sich den aktuellen Handler und setzt einen neuen {@code LocalNetworkHandler}. */
  public void before() {
    previous = MockNetworkHandler.current();
    MockNetworkHandler.useLocalNetworkHandler();
  }

  /** Stellt den Handler von vor dem Test wieder her. */
  public void after() {
    MockNetworkHandler.useNetworkHandler(previous);
  }
}
