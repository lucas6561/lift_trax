package com.lifttrax.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CliUserResolverTest {
  @AfterEach
  void clearUser() {
    System.clearProperty("lifttrax.cli.userId");
  }

  @Test
  void usesConfiguredUserWhenCommandLineOptionIsAbsent() {
    System.setProperty("lifttrax.cli.userId", "configured-user");

    assertEquals("configured-user", CliUserResolver.resolve(null));
  }

  @Test
  void explicitUserOverridesConfiguredUser() {
    System.setProperty("lifttrax.cli.userId", "configured-user");

    assertEquals("explicit-user", CliUserResolver.resolve("explicit-user"));
  }

  @Test
  void missingUserExplainsAllConfigurationOptions() {
    IllegalArgumentException error =
        assertThrows(IllegalArgumentException.class, () -> CliUserResolver.resolve(null));

    assertTrue(error.getMessage().contains("--user"));
    assertTrue(error.getMessage().contains("LIFTTRAX_CLI_USER_ID"));
    assertTrue(error.getMessage().contains("lifttrax-local.override.properties"));
  }
}
