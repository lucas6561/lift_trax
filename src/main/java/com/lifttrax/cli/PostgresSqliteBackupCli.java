package com.lifttrax.cli;

import com.lifttrax.db.PostgresSqliteBackupService;
import java.nio.file.Path;

/** Operator entry point for a complete Postgres-to-SQLite snapshot. */
public final class PostgresSqliteBackupCli {
  private PostgresSqliteBackupCli() {}

  public static void main(String[] args) throws Exception {
    Options options = parseArgs(args);
    PostgresSqliteBackupService.BackupResult result =
        PostgresSqliteBackupService.createBackup(
            Path.of(options.destination()), options.confirmOverwrite());
    System.out.println("Postgres snapshot created: " + result.backupPath());
    System.out.println("Created at: " + result.createdAtUtc());
    System.out.println("Format version: " + result.validation().formatVersion());
    System.out.println("Postgres schema version: " + result.validation().postgresSchemaVersion());
  }

  private static Options parseArgs(String... args) {
    String destination = null;
    boolean confirmOverwrite = false;
    for (String arg : args) {
      if ("--confirm-overwrite".equals(arg)) {
        confirmOverwrite = true;
      } else if (arg.startsWith("--")) {
        throw new IllegalArgumentException("Unknown option: " + arg);
      } else if (destination == null) {
        destination = arg;
      } else {
        throw new IllegalArgumentException("Unexpected argument: " + arg);
      }
    }
    if (destination == null || destination.isBlank()) {
      throw new IllegalArgumentException(
          "Destination is required. Usage: postgresSqliteBackup --args='<backup.db>'");
    }
    return new Options(destination, confirmOverwrite);
  }

  private record Options(String destination, boolean confirmOverwrite) {}
}
