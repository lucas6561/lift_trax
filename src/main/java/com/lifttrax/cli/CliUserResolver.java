package com.lifttrax.cli;

import com.lifttrax.config.LiftTraxConfig;

/** Resolves the account used by user-scoped command-line operations. */
final class CliUserResolver {
  private CliUserResolver() {}

  static String resolve(String explicitUserId) {
    if (explicitUserId != null && !explicitUserId.isBlank()) {
      return explicitUserId.trim();
    }
    String configured = LiftTraxConfig.setting("lifttrax.cli.userId", "LIFTTRAX_CLI_USER_ID", "");
    if (configured.isBlank()) {
      throw new IllegalArgumentException(
          "A user is required. Pass --user, set LIFTTRAX_CLI_USER_ID, or configure "
              + "lifttrax.cli.userId in config/lifttrax-local.override.properties.");
    }
    return configured;
  }
}
