package com.lifttrax.cli;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/** Bounded per-address throttling before expensive password operations; ignores proxy headers. */
final class AuthAttemptLimiter {
  private final Map<String, Window> windows = new HashMap<>();
  private final ReentrantLock lock = new ReentrantLock();

  boolean allow(String address, Instant now) {
    lock.lock();
    try {
      windows.entrySet().removeIf(entry -> !entry.getValue().until().isAfter(now));
      Window window = windows.get(address);
      if (window == null) {
        if (windows.size() >= 4096) {
          return false;
        }
        windows.put(address, new Window(now.plusSeconds(60), 1));
        return true;
      }
      if (window.count() >= 10) {
        return false;
      }
      windows.put(address, new Window(window.until(), window.count() + 1));
      return true;
    } finally {
      lock.unlock();
    }
  }

  private record Window(Instant until, int count) {}
}
