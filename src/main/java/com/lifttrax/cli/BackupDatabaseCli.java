package com.lifttrax.cli;

import com.lifttrax.db.DatabaseBackupService;
import java.nio.file.Path;

/** Command entry point for creating a plain SQLite LiftTrax database backup. */
public final class BackupDatabaseCli {
  private BackupDatabaseCli() {}

  public static void main(String[] args) throws Exception {
    Options options = parseArgs(args);
    String dbPath =
        options.dbPath() == null
            ? DbPathResolver.resolveFromArgsOrDefault(new String[0])
            : options.dbPath();
    DatabaseBackupService.BackupResult result =
        options.outputPath() == null
            ? DatabaseBackupService.createBackup(Path.of(dbPath))
            : DatabaseBackupService.createBackup(Path.of(dbPath), Path.of(options.outputPath()));

    System.out.println("Backup created: " + result.backupPath());
    System.out.println("Source database: " + result.source());
    System.out.println("Schema version: " + result.validation().schemaVersion());
  }

  private static Options parseArgs(String... args) {
    String dbPath = null;
    String outputPath = null;
    int index = 0;
    while (index < args.length) {
      String arg = args[index];
      if ("--output".equals(arg)) {
        if (index + 1 >= args.length) {
          throw new IllegalArgumentException("Missing value after --output");
        }
        outputPath = args[index + 1];
        index += 2;
        continue;
      }
      if (arg.startsWith("--")) {
        throw new IllegalArgumentException("Unknown option: " + arg);
      }
      if (dbPath != null) {
        throw new IllegalArgumentException("Unexpected argument: " + arg);
      }
      dbPath = arg;
      index++;
    }
    return new Options(dbPath, outputPath);
  }

  private record Options(String dbPath, String outputPath) {}
}
