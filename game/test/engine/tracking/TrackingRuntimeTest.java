package engine.tracking;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class TrackingRuntimeTest {

  @Test
  void returnsFalseWhenTrackingIsDeclined() {
    Optional<Boolean> previousConsent = TrackingRuntime.localTrackingConsent();

    try {
      TrackingRuntime.localTrackingConsent(false);
      assertEquals(Optional.of(false), TrackingRuntime.localTrackingConsent());
    } finally {
      TrackingRuntime.localTrackingConsent(previousConsent.orElse(null));
    }
  }

  @Test
  void returnsTrueWhenTrackingIsAccepted() {
    Optional<Boolean> previousConsent = TrackingRuntime.localTrackingConsent();

    try {
      TrackingRuntime.localTrackingConsent(true);
      assertEquals(Optional.of(true), TrackingRuntime.localTrackingConsent());
    } finally {
      TrackingRuntime.localTrackingConsent(previousConsent.orElse(null));
    }
  }

  @Test
  void returnsEmptyWhenConsentIsCleared() {
    Optional<Boolean> previousConsent = TrackingRuntime.localTrackingConsent();

    try {
      TrackingRuntime.localTrackingConsent(true);
      TrackingRuntime.localTrackingConsent(null);
      assertEquals(Optional.empty(), TrackingRuntime.localTrackingConsent());
    } finally {
      TrackingRuntime.localTrackingConsent(previousConsent.orElse(null));
    }
  }
}
