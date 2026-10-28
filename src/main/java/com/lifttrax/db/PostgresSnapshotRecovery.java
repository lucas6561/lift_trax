package com.lifttrax.db;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** Restores a validated application snapshot only into an explicitly selected empty database. */
public final class PostgresSnapshotRecovery {
  private PostgresSnapshotRecovery() {}

  public static RecoveryResult restore(Path artifact, HostedPostgresConfig isolatedTarget)
      throws Exception {
    Instant started = Instant.now();
    // Validate before opening or creating anything in the destination.
    PostgresSqliteBackupService.ValidationResult validation =
        PostgresSqliteBackupService.validate(artifact);
    try (Connection source =
            DriverManager.getConnection("jdbc:sqlite:" + artifact.toAbsolutePath());
        Connection target =
            HostedPostgresTrainingDataStoreProvider.openConnection(isolatedTarget)) {
      String product = target.getMetaData().getDatabaseProductName();
      if (!"PostgreSQL".equals(product) && !"H2".equals(product)) {
        throw new IllegalArgumentException(
            "Recovery target must be a new isolated Postgres database.");
      }
      requireEmptyTarget(target);
      PostgresSchemaMigrator.migrate(target);
      target.setAutoCommit(false);
      try {
        try (Statement statement = target.createStatement()) {
          statement.executeUpdate("DELETE FROM lifttrax_schema_migrations");
        }
        Map<String, Long> restored = new LinkedHashMap<>();
        for (PostgresSqliteBackupService.TableSpec table :
            PostgresSqliteBackupService.applicationTables()) {
          long count = copy(source, target, table);
          if (count != validation.rowCounts().get(table.name())) {
            throw new IllegalStateException("Recovery row-count mismatch: " + table.name());
          }
          restored.put(table.name(), count);
        }
        SnapshotIntegrity.validate(target);
        long next;
        try (Statement statement = target.createStatement();
            ResultSet rows =
                statement.executeQuery(
                    "SELECT COALESCE(MAX(web_execution_id), 0) + 1 FROM executions")) {
          if (!rows.next()) {
            throw new IllegalStateException("Recovery execution sequence could not be read.");
          }
          next = rows.getLong(1);
        }
        try (Statement statement = target.createStatement()) {
          statement.execute(
              "ALTER TABLE executions ALTER COLUMN web_execution_id RESTART WITH " + next);
        }
        String recoveryPoint = validation.createdAtUtc();
        target.commit();
        return new RecoveryResult(
            recoveryPoint,
            Duration.between(started, Instant.now()).toMillis(),
            Map.copyOf(restored));
      } catch (Exception e) {
        target.rollback();
        throw new IllegalStateException(
            "Recovery failed; do not enable traffic to the isolated target.", e);
      }
    }
  }

  private static void requireEmptyTarget(Connection target) throws Exception {
    try (ResultSet tables =
        target.getMetaData().getTables(null, null, null, new String[] {"TABLE"})) {
      while (tables.next()) {
        String name = tables.getString("TABLE_NAME");
        String schema = tables.getString("TABLE_SCHEM");
        if (!"information_schema".equalsIgnoreCase(schema)
            && !"pg_catalog".equalsIgnoreCase(schema)) {
          throw new IllegalArgumentException(
              "Recovery refuses an initialized target. Select a new empty isolated database. Existing table: "
                  + name);
        }
      }
    }
  }

  private static long copy(
      Connection source, Connection target, PostgresSqliteBackupService.TableSpec table)
      throws Exception {
    String columns = String.join(", ", table.columns());
    String placeholders = String.join(", ", table.columns().stream().map(ignored -> "?").toList());
    try (Statement read = source.createStatement();
        ResultSet rows = read.executeQuery("SELECT " + columns + " FROM " + table.name());
        Statement describe = target.createStatement();
        ResultSet empty =
            describe.executeQuery("SELECT " + columns + " FROM " + table.name() + " WHERE 1 = 0");
        PreparedStatement insert =
            target.prepareStatement(
                "INSERT INTO "
                    + table.name()
                    + " ("
                    + columns
                    + ") VALUES ("
                    + placeholders
                    + ")")) {
      ResultSetMetaData types = empty.getMetaData();
      long count = 0;
      while (rows.next()) {
        for (int i = 1; i <= table.columns().size(); i++) {
          Object value = rows.getObject(i);
          int type = types.getColumnType(i);
          if (value == null) {
            insert.setNull(i, type);
          } else if (type == Types.BOOLEAN || type == Types.BIT) {
            insert.setBoolean(i, rows.getBoolean(i));
          } else if (type == Types.DATE) {
            insert.setObject(i, LocalDate.parse(value.toString()));
          } else if (type == Types.TIMESTAMP) {
            insert.setObject(i, LocalDateTime.parse(value.toString().replace(' ', 'T')));
          } else {
            insert.setObject(i, value);
          }
        }
        insert.executeUpdate();
        count++;
      }
      return count;
    }
  }

  public record RecoveryResult(
      String recoveryPointUtc, long recoveryTimeMillis, Map<String, Long> rowCounts) {}
}
