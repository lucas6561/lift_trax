package com.lifttrax.cli;

import com.lifttrax.db.HostedPostgresConfig;
import com.lifttrax.db.PostgresSnapshotRecovery;
import java.nio.file.Path;

/** Explicit operator recovery into a separately configured new isolated database. */
public final class PostgresRecoveryCli {
  private PostgresRecoveryCli() {}

  public static void main(String[] args) throws Exception {
    if (args.length != 2 || !"--confirm-isolated-target".equals(args[1])) {
      throw new IllegalArgumentException(
          "Usage: postgresRecovery --args='<snapshot.db> --confirm-isolated-target'. Set LIFTTRAX_RECOVERY_JDBC_URL, LIFTTRAX_RECOVERY_JDBC_USER, and LIFTTRAX_RECOVERY_JDBC_PASSWORD for a new isolated target.");
    }
    String url = System.getenv("LIFTTRAX_RECOVERY_JDBC_URL");
    if (url == null || !url.startsWith("jdbc:postgresql:")) {
      throw new IllegalArgumentException(
          "An explicit Postgres LIFTTRAX_RECOVERY_JDBC_URL is required.");
    }
    HostedPostgresConfig target =
        new HostedPostgresConfig(
            url,
            System.getenv().getOrDefault("LIFTTRAX_RECOVERY_JDBC_USER", ""),
            System.getenv().getOrDefault("LIFTTRAX_RECOVERY_JDBC_PASSWORD", ""));
    var result = PostgresSnapshotRecovery.restore(Path.of(args[0]), target);
    System.out.println("Isolated application-data restore validated.");
    System.out.println("Recovery point (UTC): " + result.recoveryPointUtc());
    System.out.println("Recovery time (ms): " + result.recoveryTimeMillis());
    System.out.println("Restored table counts: " + result.rowCounts());
    System.out.println(
        "Keep the target isolated until account exports and authenticated smoke checks pass. Provider auth and configuration require separate recovery.");
  }
}
