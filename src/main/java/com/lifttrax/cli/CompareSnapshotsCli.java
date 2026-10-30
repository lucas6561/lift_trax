package com.lifttrax.cli;

import com.lifttrax.db.PostgresSnapshotComparison;
import java.nio.file.Path;

/** Read-only operator check that a transport/configuration change preserved application data. */
public final class CompareSnapshotsCli {
  private CompareSnapshotsCli() {}

  public static void main(String[] args) throws Exception {
    if (args.length != 2) {
      throw new IllegalArgumentException("Usage: CompareSnapshotsCli <before.db> <after.db>");
    }
    PostgresSnapshotComparison.requireIdentical(Path.of(args[0]), Path.of(args[1]));
    System.out.println(
        "Validated snapshots contain identical application rows, IDs, and ownership.");
  }
}
