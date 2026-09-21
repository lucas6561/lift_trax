package com.lifttrax.cli;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SetLocalPasswordCliTest {
  @Test
  void requiresExplicitAccountAndDoesNotAcceptPasswordArguments() {
    assertEquals("alice", SetLocalPasswordCli.parseUser(new String[] {"--user", "alice"}));
    assertThrows(
        IllegalArgumentException.class, () -> SetLocalPasswordCli.parseUser(new String[] {}));
    assertThrows(
        IllegalArgumentException.class,
        () -> SetLocalPasswordCli.parseUser(new String[] {"--user", " "}));
    assertThrows(
        IllegalArgumentException.class,
        () -> SetLocalPasswordCli.parseUser(new String[] {"--password", "secret"}));
    assertThrows(
        IllegalArgumentException.class,
        () -> SetLocalPasswordCli.parseUser(new String[] {"--user", "alice", "secret"}));
  }
}
