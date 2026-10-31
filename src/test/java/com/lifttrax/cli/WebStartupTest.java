package com.lifttrax.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lifttrax.config.StartupDiagnostics;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WebStartupTest {
  @Test
  void hostedSettingsFailBeforeDatabaseWorkAndOnlyReportNames() {
    var failure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                WebStartup.configuration(
                    new String[] {"10000"},
                    (property, environment) ->
                        environment.equals("LIFTTRAX_AUTH_MODE") ? "supabase" : ""));
    for (String key :
        new String[] {
          "LIFTTRAX_HOSTED_JDBC_URL",
          "LIFTTRAX_HOSTED_JDBC_USER",
          "LIFTTRAX_HOSTED_JDBC_PASSWORD",
          "LIFTTRAX_AUTH_SESSION_SECRET",
          "LIFTTRAX_SUPABASE_URL",
          "LIFTTRAX_SUPABASE_ANON_KEY",
          "LIFTTRAX_AUTH_REDIRECT_URI"
        }) {
      assertTrue(failure.getMessage().contains(key));
    }
  }

  @Test
  void validHostedAndLocalConfigurationsRetainPortBehavior() {
    Map<String, String> settings = new HashMap<>();
    settings.put("LIFTTRAX_AUTH_MODE", "supabase");
    assertEquals(
        10000,
        WebStartup.configuration(
            new String[] {"10000"},
            (property, environment) -> settings.getOrDefault(environment, "configured")));
    assertEquals(
        8080,
        WebStartup.configuration(
            new String[] {},
            (property, environment) ->
                environment.equals("LIFTTRAX_HOSTED_JDBC_URL") ? "configured" : ""));
  }

  @Test
  void malformedPortAndAuthModeNeverEchoTheirValue() {
    for (String[] args : new String[][] {{"private-token"}, {"-1"}, {"65536"}, {"1", "2"}}) {
      var failure =
          assertThrows(
              IllegalArgumentException.class,
              () -> WebStartup.configuration(args, (property, environment) -> ""));
      assertFalse(failure.getMessage().contains("private-token"));
    }
    var failure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                WebStartup.configuration(
                    new String[] {}, (property, environment) -> "private-token"));
    assertTrue(failure.getMessage().contains("LIFTTRAX_AUTH_MODE"));
    assertFalse(failure.getMessage().contains("private-token"));
  }

  @Test
  void fatalDiagnosticsKeepStacksSqlStateAndCausalLinksWithoutSecretMessages() {
    var root = new java.net.SocketException("password=private-token");
    var sql =
        new SQLException("jdbc:postgresql://private-host?password=private-token", "08001", root);
    sql.addSuppressed(new IllegalStateException("private-token"));
    sql.setNextException(new SQLException("private-token", "28P01"));
    root.initCause(sql); // Cycle protection applies to cause, suppressed and next-exception graphs.
    var bytes = new ByteArrayOutputStream();
    StartupDiagnostics.printFailure(sql, new PrintStream(bytes));
    String output = bytes.toString(java.nio.charset.StandardCharsets.UTF_8);
    assertTrue(output.contains("sqlState=08001"));
    assertTrue(output.contains("caused-by=java.net.SocketException"));
    assertTrue(output.contains("suppressed=java.lang.IllegalStateException"));
    assertTrue(output.contains("next-sql-exception=java.sql.SQLException sqlState=28P01"));
    assertTrue(output.contains("WebStartupTest.java:"));
    assertFalse(output.contains("private-token"));
    assertFalse(output.contains("private-host"));
  }
}
