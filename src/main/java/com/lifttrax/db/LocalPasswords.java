package com.lifttrax.db;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Versioned, salted password hashes using the JDK's PBKDF2 implementation. */
final class LocalPasswords {
  private static final int ITERATIONS = 600_000;
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final String DUMMY = hash("unused dummy password");

  private LocalPasswords() {}

  static String hash(String password) {
    if (password == null || password.isEmpty()) {
      throw new IllegalArgumentException("Password must contain at least one character.");
    }
    byte[] salt = new byte[16];
    RANDOM.nextBytes(salt);
    return "pbkdf2-sha256$"
        + ITERATIONS
        + "$"
        + Base64.getEncoder().encodeToString(salt)
        + "$"
        + Base64.getEncoder().encodeToString(derive(password, salt));
  }

  static boolean matches(String password, String encoded) {
    if (password == null || password.isEmpty()) {
      return false;
    }
    String candidate = encoded == null || encoded.isBlank() ? DUMMY : encoded;
    try {
      String[] parts = candidate.split("\\$", -1);
      if (parts.length != 4
          || !"pbkdf2-sha256".equals(parts[0])
          || !parts[1].equals(Integer.toString(ITERATIONS))) {
        return false;
      }
      byte[] salt = Base64.getDecoder().decode(parts[2]);
      byte[] expected = Base64.getDecoder().decode(parts[3]);
      if (salt.length != 16 || expected.length != 32) {
        return false;
      }
      boolean match = MessageDigest.isEqual(expected, derive(password, salt));
      return encoded != null && !encoded.isBlank() && match;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  private static byte[] derive(String password, byte[] salt) {
    PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256);
    try {
      return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
    } catch (java.security.GeneralSecurityException e) {
      throw new IllegalStateException("Password hashing is unavailable.", e);
    } finally {
      spec.clearPassword();
    }
  }
}
