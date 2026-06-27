package com.lifttrax.cli;

import com.lifttrax.db.HostedLocalDatabaseImportService;
import com.lifttrax.db.TrainingDataStore;
import com.lifttrax.db.TrainingDataStoreProvider;
import java.nio.file.Path;

/** Command entry point for importing a local LiftTrax database into a hosted account. */
public final class ImportHostedDatabaseCli {
  private ImportHostedDatabaseCli() {}

  public static void main(String[] args) throws Exception {
    Options options = parseArgs(args);
    HostedLocalDatabaseImportService.ImportPreview preview =
        HostedLocalDatabaseImportService.preview(Path.of(options.sourcePath()));
    if (options.previewOnly()) {
      printPreview(preview);
      return;
    }
    try (TrainingDataStoreProvider provider =
        TrainingDataStoreProvider.fromEnvironment("data/lifts.db")) {
      TrainingDataStore target = provider.forUser(options.userId());
      HostedLocalDatabaseImportService.ImportResult result =
          HostedLocalDatabaseImportService.importDatabase(Path.of(options.sourcePath()), target);
      printResult(result);
    }
  }

  private static void printPreview(HostedLocalDatabaseImportService.ImportPreview preview) {
    System.out.println("Import source: " + preview.source());
    System.out.println("Schema version: " + preview.schemaVersion());
    System.out.println("Lifts: " + preview.liftCount());
    System.out.println("Executions: " + preview.executionCount());
    System.out.println("Fingerprint: " + preview.fingerprint());
  }

  private static void printResult(HostedLocalDatabaseImportService.ImportResult result) {
    System.out.println("Import id: " + result.importId());
    System.out.println("Duplicate: " + result.duplicate());
    System.out.println("Inserted lifts: " + result.insertedLifts());
    System.out.println("Inserted executions: " + result.insertedExecutions());
    System.out.println("Skipped executions: " + result.skippedExecutions());
  }

  private static Options parseArgs(String... args) {
    String sourcePath = null;
    String userId = null;
    boolean previewOnly = false;
    int index = 0;
    while (index < args.length) {
      String arg = args[index];
      if ("--user".equals(arg)) {
        if (index + 1 >= args.length) {
          throw new IllegalArgumentException("Missing value after --user");
        }
        userId = args[index + 1];
        index += 2;
        continue;
      }
      if ("--preview".equals(arg)) {
        previewOnly = true;
        index++;
        continue;
      }
      if (arg.startsWith("--")) {
        throw new IllegalArgumentException("Unknown option: " + arg);
      }
      if (sourcePath != null) {
        throw new IllegalArgumentException("Unexpected argument: " + arg);
      }
      sourcePath = arg;
      index++;
    }
    if (sourcePath == null) {
      throw new IllegalArgumentException("Source database path is required.");
    }
    if (!previewOnly && (userId == null || userId.isBlank())) {
      throw new IllegalArgumentException("--user is required unless --preview is used.");
    }
    return new Options(sourcePath, userId, previewOnly);
  }

  private record Options(String sourcePath, String userId, boolean previewOnly) {}
}
