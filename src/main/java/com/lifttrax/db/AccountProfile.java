package com.lifttrax.db;

/** User-facing account identity layered over the immutable authentication subject. */
public record AccountProfile(String authUserId, String username, String email) {
  public AccountProfile {
    authUserId = authUserId == null ? "" : authUserId.trim();
    username = username == null ? "" : username.trim();
    email = email == null ? "" : email.trim();
  }

  public String displayLabel() {
    if (!username.isBlank()) {
      return username;
    }
    return email.isBlank() ? authUserId : email;
  }
}
