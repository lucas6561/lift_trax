package com.lifttrax.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class AuthAttemptLimiterTest {
  @Test
  void limitsRepeatedAttemptsRecoversAfterOneMinuteAndBoundsMemory() {
    var limiter = new AuthAttemptLimiter();
    var now = Instant.parse("2026-09-20T00:00:00Z");
    for (int i = 0; i < 10; i++) assertTrue(limiter.allow("same", now));
    assertFalse(limiter.allow("same", now));
    assertTrue(limiter.allow("different", now));
    assertFalse(limiter.allow("same", now.plusSeconds(59)));
    assertTrue(limiter.allow("same", now.plusSeconds(60)));
    for (int i = 0; i < 4095; i++) assertTrue(limiter.allow("ip-" + i, now.plusSeconds(60)));
    assertFalse(limiter.allow("overflow", now.plusSeconds(60)));
    assertTrue(limiter.allow("overflow", now.plusSeconds(120)));
  }
}
