package com.lifttrax.cli;

import com.lifttrax.db.DatabaseBackupService;
import java.nio.file.Path;

/** Command entry point for restoring a plain SQLite LiftTrax database backup. */
public final class RestoreDatabaseCli {
  private RestoreDatabaseCli() {}

  public static void main(String[] args) throws Exception {
    Options options = parseArgs(args);
    String dbPath =
        options.dbPath() == null
            ? DbPathResolver.resolveFromArgsOrDefault(new String[0])
            : options.dbPath();
    DatabaseBackupService.RestoreResult result =
        DatabaseBackupService.restoreBackup(
            Path.of(options.backupPath()), Path.of(dbPath), options.confirmOverwrite());

    System.out.println("Restored database: " + result.restoredPath());
    System.out.println("Backup source: " + result.backupPath());
    if (result.preRestoreBackupPath() != null) {
      System.out.println("Previous database saved as: " + result.preRestoreBackupPath());
    }
    System.out.println("Schema version: " + result.validation().schemaVersion());
  }

  private static Options parseArgs(String... args) {
    String backupPath = null;
    String dbPath = null;
    boolean confirmOverwrite = false;
    for (String arg : args) {
      if ("--confirm-overwrite".equals(arg)) {
        confirmOverwrite = true;
        continue;
      }
      if (arg.startsWith("--")) {
        throw new IllegalArgumentException("Unknown option: " + arg);
      }
      if (backupPath == null) {
        backupPath = arg;
        continue;
      }
      if (dbPath != null) {
        throw new IllegalArgumentException("Unexpected argument: " + arg);
      }
      dbPath = arg;
    }
    if (backupPath == null) {
      throw new IllegalArgumentException(
          "Backup path is required. Usage: restoreDatabase --args='<backup.db> [dbPath]"
              + " --confirm-overwrite'");
    }
    return new Options(backupPath, dbPath, confirmOverwrite);
  }

  private record Options(String backupPath, String dbPath, boolean confirmOverwrite) {}
}
