package com.lifttrax.cli;

import com.lifttrax.config.LiftTraxConfig;
import com.lifttrax.db.TrainingDataStoreProvider;
import java.io.Console;
import java.util.Arrays;

/** Server-owner bootstrap and recovery without passwords in arguments or shell history. */
public final class SetLocalPasswordCli {
  private SetLocalPasswordCli() {}

  public static void main(String[] args) throws Exception {
    String user = parseUser(args);
    if (!"local"
        .equalsIgnoreCase(
            LiftTraxConfig.setting("lifttrax.auth.mode", "LIFTTRAX_AUTH_MODE", "local"))) {
      throw new IllegalArgumentException("Use your identity provider to manage hosted passwords.");
    }
    Console console = System.console();
    if (console == null) {
      throw new IllegalStateException(
          "Run set-local-password.bat in an interactive terminal to enter a hidden password.");
    }
    try (TrainingDataStoreProvider provider = TrainingDataStoreProvider.fromEnvironment()) {
      String id = provider.resolveAuthUserId(user);
      console.printf("Set password for %s%n", provider.accountFor(id, "").displayLabel());
      char[] password = console.readPassword("New password (at least one character): ");
      char[] confirmation = console.readPassword("Confirm password: ");
      try {
        if (password == null || confirmation == null || !Arrays.equals(password, confirmation)) {
          throw new IllegalArgumentException("Passwords do not match. Nothing changed.");
        }
        provider.resetLocalPassword(id, new String(password));
        console.printf("Password saved. Existing sessions are signed out.%n");
      } finally {
        if (password != null) {
          Arrays.fill(password, '\0');
        }
        if (confirmation != null) {
          Arrays.fill(confirmation, '\0');
        }
      }
    }
  }

  static String parseUser(String... args) {
    if (args.length != 2 || !"--user".equals(args[0]) || args[1].isBlank()) {
      throw new IllegalArgumentException(
          "Usage: set-local-password.bat --user <username-or-account-id>");
    }
    return args[1];
  }
}
