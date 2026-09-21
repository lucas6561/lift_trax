package com.lifttrax.db;

import static org.junit.jupiter.api.Assertions.*;

import com.lifttrax.models.LiftRegion;
import com.lifttrax.models.LiftType;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LocalPasswordsTest {
  private static final String PASSWORD = "correct horse battery staple";

  @Test
  void hashesUseIndependentSaltsAndPreserveSpacesUnicodeAndCase() {
    String password = "  \u00e9lan horse battery Staple  ";
    String first = LocalPasswords.hash(password);
    assertNotEquals(first, LocalPasswords.hash(password));
    assertFalse(first.contains(password));
    assertTrue(LocalPasswords.matches(password, first));
    assertFalse(LocalPasswords.matches(password.trim(), first));
    assertFalse(LocalPasswords.matches(password.toLowerCase(java.util.Locale.ROOT), first));
    assertFalse(LocalPasswords.matches(password, null));
    assertFalse(LocalPasswords.matches(password, ""));
    assertFalse(LocalPasswords.matches(null, first));
    assertFalse(LocalPasswords.matches("", first));
    for (String malformed :
        List.of(
            "bad",
            "pbkdf2-sha256$1$a$b",
            "pbkdf2-sha256$600000$bad!$bad!",
            "pbkdf2-sha256$600000$YQ==$YQ==")) {
      assertFalse(LocalPasswords.matches(password, malformed));
    }
    assertThrows(IllegalArgumentException.class, () -> LocalPasswords.hash(""));
    assertThrows(IllegalArgumentException.class, () -> LocalPasswords.hash(null));
    for (String valid : List.of("x", " ", "é", "x".repeat(1024))) {
      assertTrue(LocalPasswords.matches(valid, LocalPasswords.hash(valid)));
    }
  }

  @Test
  void registrationChangesAndResetsAcceptAnyNonemptyPassword() throws Exception {
    try (var provider = new HostedPostgresTrainingDataStoreProvider(config())) {
      var account = provider.createLocalAccount("minimal", "", "x");
      assertTrue(provider.authenticateLocal("minimal", "x").isPresent());
      provider.changeLocalPassword(account.authUserId(), "x", " ");
      assertTrue(provider.authenticateLocal("minimal", " ").isPresent());
      assertTrue(provider.authenticateLocal("minimal", "").isEmpty());
      String longPassword = "x".repeat(1024);
      provider.resetLocalPassword(account.authUserId(), longPassword);
      assertTrue(provider.authenticateLocal("minimal", longPassword).isPresent());
      assertTrue(provider.authenticateLocal("minimal", longPassword.substring(0, 128)).isEmpty());
      assertThrows(
          IllegalArgumentException.class,
          () -> provider.resetLocalPassword(account.authUserId(), ""));
      assertTrue(provider.authenticateLocal("minimal", longPassword).isPresent());
    }
  }

  @Test
  void existingAccountRequiresOperatorSetupAndKeepsItsIdentityAndData() throws Exception {
    var config = config();
    try (var provider = new HostedPostgresTrainingDataStoreProvider(config)) {
      var store = provider.forUser("legacy-id");
      provider.updateUsername("legacy-id", "legacy");
      store.addLift("Bench", LiftRegion.UPPER, LiftType.BENCH_PRESS, List.of(), "kept");
      // Recreate the pre-password schema with existing user data, then exercise the upgrade.
      try (var connection = DriverManager.getConnection(config.jdbcUrl());
          var statement = connection.createStatement()) {
        statement.execute("ALTER TABLE app_users DROP COLUMN password_hash");
        statement.execute("ALTER TABLE app_users DROP COLUMN password_version");
        statement.execute("DELETE FROM lifttrax_schema_migrations WHERE version = 9");
      }
      try (var upgraded = new HostedPostgresTrainingDataStoreProvider(config)) {
        assertEquals("", upgraded.localPasswordVersion("legacy-id"));
        assertEquals("kept", upgraded.forUserIdentifier("legacy").getLift("Bench").notes());
      }
      assertTrue(provider.authenticateLocal("legacy", "").isEmpty());
      assertTrue(provider.authenticateLocal("legacy", PASSWORD).isEmpty());
      assertTrue(provider.authenticateLocal("unknown", PASSWORD).isEmpty());
      assertTrue(provider.authenticateLocal("", PASSWORD).isEmpty());
      assertThrows(
          IllegalArgumentException.class, () -> provider.resetLocalPassword("unknown", PASSWORD));
      provider.resetLocalPassword("legacy-id", PASSWORD);
      var verified = provider.authenticateLocal("LEGACY", PASSWORD).orElseThrow();
      assertEquals("legacy-id", verified.authUserId());
      assertFalse(verified.passwordVersion().isBlank());
      assertEquals(verified, provider.authenticateLocal("legacy-id", PASSWORD).orElseThrow());
      assertTrue(provider.authenticateLocal("legacy", "wrong").isEmpty());
      assertThrows(
          IllegalArgumentException.class,
          () -> provider.changeLocalPassword("legacy-id", "wrong", "a new long password"));
      assertThrows(
          IllegalArgumentException.class,
          () -> provider.changeLocalPassword("legacy-id", PASSWORD, ""));
      assertEquals(verified.passwordVersion(), provider.localPasswordVersion("legacy-id"));
      provider.changeLocalPassword("legacy-id", PASSWORD, "a new long password");
      assertNotEquals(verified.passwordVersion(), provider.localPasswordVersion("legacy-id"));
      assertTrue(provider.authenticateLocal("legacy", PASSWORD).isEmpty());
      provider.resetLocalPassword("legacy-id", PASSWORD);
      try (var reopened = new HostedPostgresTrainingDataStoreProvider(config)) {
        assertTrue(reopened.authenticateLocal("legacy", PASSWORD).isPresent());
        assertEquals("kept", reopened.forUserIdentifier("legacy").getLift("Bench").notes());
      }
      try (var connection = DriverManager.getConnection(config.jdbcUrl());
          var statement = connection.createStatement();
          var rows =
              statement.executeQuery(
                  "SELECT password_hash FROM app_users WHERE auth_user_id = 'legacy-id'")) {
        assertTrue(rows.next());
        assertNotEquals(PASSWORD, rows.getString(1));
        assertTrue(rows.getString(1).startsWith("pbkdf2-sha256$600000$"));
      }
    }
  }

  @Test
  void registrationIsAtomicWithPasswordAndNeverReplacesCredentialsOnDuplicate() throws Exception {
    try (var provider = new HostedPostgresTrainingDataStoreProvider(config())) {
      assertThrows(
          IllegalArgumentException.class, () -> provider.createLocalAccount("alice", "", ""));
      assertThrows(IllegalArgumentException.class, () -> provider.resolveAuthUserId("alice"));
      var account = provider.createLocalAccount("alice", "", PASSWORD);
      var verified = provider.authenticateLocal("alice", PASSWORD).orElseThrow();
      assertEquals(account.authUserId(), verified.authUserId());
      assertThrows(
          IllegalArgumentException.class,
          () -> provider.createLocalAccount("ALICE", "", "different long password"));
      assertEquals(verified, provider.authenticateLocal("alice", PASSWORD).orElseThrow());
      assertTrue(provider.authenticateLocal("alice", "different long password").isEmpty());
    }
  }

  private static HostedPostgresConfig config() {
    return new HostedPostgresConfig(
        "jdbc:h2:mem:password_"
            + UUID.randomUUID()
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "",
        "");
  }
}
