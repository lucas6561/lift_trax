package com.lifttrax.config;

import java.io.PrintStream;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Startup events and causal stacks without arbitrary exception messages or configuration values.
 */
public final class StartupDiagnostics {
  private StartupDiagnostics() {}

  public static void event(String stage, String status) {
    System.err.println(Instant.now() + " startup stage=" + stage + " " + status);
  }

  public static void failure(Throwable failure) {
    printFailure(failure, System.err);
  }

  public static void printFailure(Throwable failure, PrintStream output) {
    Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
    printCause(failure, "failure", output, visited);
  }

  private static void printCause(
      Throwable failure, String relationship, PrintStream output, Set<Throwable> visited) {
    if (failure == null || !visited.add(failure)) {
      return;
    }
    String sqlState = failure instanceof SQLException sql ? sql.getSQLState() : null;
    output.println(
        relationship
            + "="
            + failure.getClass().getName()
            + (sqlState != null && sqlState.matches("[A-Z0-9]{5}") ? " sqlState=" + sqlState : "")
            + " (message omitted to protect secrets)");
    for (StackTraceElement frame : failure.getStackTrace()) {
      output.println("\tat " + frame);
    }
    printCause(failure.getCause(), "caused-by", output, visited);
    for (Throwable suppressed : failure.getSuppressed()) {
      printCause(suppressed, "suppressed", output, visited);
    }
    if (failure instanceof SQLException sql) {
      printCause(sql.getNextException(), "next-sql-exception", output, visited);
    }
  }
}
