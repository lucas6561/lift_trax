package com.lifttrax.cli;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Stable browser storage and form scope, independent of mutable usernames. */
final class BrowserAccountScope {
  private BrowserAccountScope() {}

  static String forUser(String userId) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest(String.valueOf(userId).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest, 0, 12);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Could not scope browser data to the signed-in user.", e);
    }
  }
}
