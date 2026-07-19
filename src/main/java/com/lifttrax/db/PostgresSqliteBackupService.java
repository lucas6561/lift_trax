package com.lifttrax.db;

import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Creates a complete point-in-time SQLite snapshot of LiftTrax-owned Postgres data. */
public final class PostgresSqliteBackupService {
  public static final int FORMAT_VERSION = 1;
  private static final DateTimeFormatter FILE_TIMESTAMP =
      DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss'Z'").withZone(ZoneOffset.UTC);
  private static final List<TableSpec> APPLICATION_TABLES = applicationTables();

  private PostgresSqliteBackupService() {}

  public static BackupResult createBackup(Path destination, boolean confirmOverwrite)
      throws Exception {
    return createBackup(
        HostedPostgresConfig.fromEnvironment(),
        destination,
        confirmOverwrite,
        BackupObserver.NONE,
        Clock.systemUTC());
  }

  static BackupResult createBackup(
      HostedPostgresConfig config, Path destination, boolean confirmOverwrite) throws Exception {
    return createBackup(
        config, destination, confirmOverwrite, BackupObserver.NONE, Clock.systemUTC());
  }

  static BackupResult createBackup(
      HostedPostgresConfig config,
      Path destination,
      boolean confirmOverwrite,
      BackupObserver observer)
      throws Exception {
    return createBackup(config, destination, confirmOverwrite, observer, Clock.systemUTC());
  }

  static BackupResult createBackup(
      HostedPostgresConfig config,
      Path destination,
      boolean confirmOverwrite,
      BackupObserver observer,
      Clock clock)
      throws Exception {
    Instant snapshotTime = clock.instant();
    Path finalPath = timestampedDestination(destination, snapshotTime);
    if (Files.exists(finalPath) && !confirmOverwrite) {
      throw new IllegalArgumentException(
          "Refusing to overwrite "
              + finalPath
              + ". Re-run with --confirm-overwrite after verifying the destination.");
    }
    Path parent = finalPath.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    Path temporaryPath =
        finalPath.resolveSibling(finalPath.getFileName() + ".tmp-" + UUID.randomUUID());
    Map<String, Long> sourceCounts = new LinkedHashMap<>();
    String createdAt = snapshotTime.toString();

    try (Connection source = HostedPostgresTrainingDataStoreProvider.openConnection(config)) {
      source.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
      source.setAutoCommit(false);
      try {
        try (Connection target = DriverManager.getConnection("jdbc:sqlite:" + temporaryPath)) {
          target.setAutoCommit(false);
          createBackupSchema(target);
          for (TableSpec table : APPLICATION_TABLES) {
            observer.beforeTable(table.name());
            long copied = copyTable(source, target, table);
            sourceCounts.put(table.name(), copied);
          }
          writeMetadata(target, createdAt, sourceCounts);
          target.commit();
        }
        ValidationResult validation = validate(temporaryPath, sourceCounts);
        publish(temporaryPath, finalPath, confirmOverwrite);
        source.rollback();
        return new BackupResult(finalPath, createdAt, validation);
      } catch (Exception e) {
        source.rollback();
        throw e;
      }
    } catch (Exception e) {
      Files.deleteIfExists(temporaryPath);
      throw new IllegalStateException(
          "Failed to create Postgres-to-SQLite backup at " + finalPath + ": " + e.getMessage(), e);
    }
  }

  private static Path timestampedDestination(Path destination, Instant snapshotTime) {
    Path requested = destination.toAbsolutePath().normalize();
    String fileName = requested.getFileName().toString();
    int extensionAt = fileName.lastIndexOf('.');
    String stem = extensionAt > 0 ? fileName.substring(0, extensionAt) : fileName;
    String extension = extensionAt > 0 ? fileName.substring(extensionAt) : "";
    String timestampedName = stem + "-" + FILE_TIMESTAMP.format(snapshotTime) + extension;
    return requested.resolveSibling(timestampedName);
  }

  public static ValidationResult validate(Path backupPath) throws SQLException {
    Path path = backupPath.toAbsolutePath().normalize();
    if (!Files.isRegularFile(path)) {
      throw new IllegalArgumentException("Backup file not found: " + path);
    }
    Map<String, Long> expected = new LinkedHashMap<>();
    try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + path);
        PreparedStatement statement =
            connection.prepareStatement(
                "SELECT table_name, row_count FROM lifttrax_backup_row_counts ORDER BY table_name");
        ResultSet resultSet = statement.executeQuery()) {
      while (resultSet.next()) {
        expected.put(resultSet.getString(1), resultSet.getLong(2));
      }
    }
    return validate(path, expected);
  }

  private static ValidationResult validate(Path backupPath, Map<String, Long> expectedCounts)
      throws SQLException {
    Map<String, Long> actualCounts = new LinkedHashMap<>();
    try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + backupPath)) {
      int formatVersion = metadataInt(connection, "format_version");
      int schemaVersion = metadataInt(connection, "postgres_schema_version");
      if (formatVersion != FORMAT_VERSION) {
        throw new IllegalStateException("Unsupported backup format version: " + formatVersion);
      }
      if (schemaVersion != PostgresSchemaMigrator.currentVersion()) {
        throw new IllegalStateException(
            "Backup schema version "
                + schemaVersion
                + " does not match application schema version "
                + PostgresSchemaMigrator.currentVersion());
      }
      for (TableSpec table : APPLICATION_TABLES) {
        if (!expectedCounts.containsKey(table.name())) {
          throw new IllegalStateException(
              "Backup is missing row-count metadata for " + table.name());
        }
        long actual = rowCount(connection, table.name());
        long expected = expectedCounts.get(table.name());
        if (actual != expected) {
          throw new IllegalStateException(
              "Backup row count mismatch for "
                  + table.name()
                  + ": expected "
                  + expected
                  + ", found "
                  + actual);
        }
        actualCounts.put(table.name(), actual);
      }
      return new ValidationResult(formatVersion, schemaVersion, Map.copyOf(actualCounts));
    }
  }

  private static void createBackupSchema(Connection connection) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      for (TableSpec table : APPLICATION_TABLES) {
        statement.execute(table.createSql());
      }
      statement.execute(
          """
              CREATE TABLE lifttrax_backup_metadata (
                  metadata_key TEXT PRIMARY KEY,
                  metadata_value TEXT NOT NULL
              )
              """);
      statement.execute(
          """
              CREATE TABLE lifttrax_backup_row_counts (
                  table_name TEXT PRIMARY KEY,
                  row_count INTEGER NOT NULL
              )
              """);
    }
  }

  private static long copyTable(Connection source, Connection target, TableSpec table)
      throws SQLException {
    String columns = String.join(", ", table.columns());
    String placeholders = String.join(", ", table.columns().stream().map(ignored -> "?").toList());
    String selectSql = "SELECT " + columns + " FROM " + table.name();
    String insertSql =
        "INSERT INTO " + table.name() + " (" + columns + ") VALUES (" + placeholders + ")";
    long rows = 0;
    try (PreparedStatement select = source.prepareStatement(selectSql);
        ResultSet resultSet = select.executeQuery();
        PreparedStatement insert = target.prepareStatement(insertSql)) {
      while (resultSet.next()) {
        for (int index = 1; index <= table.columns().size(); index++) {
          Object value = resultSet.getObject(index);
          if (value instanceof TemporalAccessor) {
            insert.setString(index, value.toString());
          } else {
            insert.setObject(index, value);
          }
        }
        insert.executeUpdate();
        rows++;
      }
    }
    return rows;
  }

  private static void writeMetadata(
      Connection connection, String createdAt, Map<String, Long> sourceCounts) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO lifttrax_backup_metadata (metadata_key, metadata_value) VALUES (?, ?)")) {
      insertMetadata(statement, "format_version", Integer.toString(FORMAT_VERSION));
      insertMetadata(
          statement,
          "postgres_schema_version",
          Integer.toString(PostgresSchemaMigrator.currentVersion()));
      insertMetadata(statement, "created_at_utc", createdAt);
    }
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO lifttrax_backup_row_counts (table_name, row_count) VALUES (?, ?)")) {
      for (Map.Entry<String, Long> entry : sourceCounts.entrySet()) {
        statement.setString(1, entry.getKey());
        statement.setLong(2, entry.getValue());
        statement.executeUpdate();
      }
    }
  }

  private static void insertMetadata(PreparedStatement statement, String key, String value)
      throws SQLException {
    statement.setString(1, key);
    statement.setString(2, value);
    statement.executeUpdate();
  }

  private static int metadataInt(Connection connection, String key) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "SELECT metadata_value FROM lifttrax_backup_metadata WHERE metadata_key = ?")) {
      statement.setString(1, key);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          throw new IllegalStateException("Backup metadata is missing " + key);
        }
        return Integer.parseInt(resultSet.getString(1));
      }
    }
  }

  private static long rowCount(Connection connection, String table) throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
      return resultSet.next() ? resultSet.getLong(1) : 0;
    }
  }

  private static void publish(Path temporary, Path destination, boolean replace) throws Exception {
    StandardCopyOption[] options =
        replace
            ? new StandardCopyOption[] {
              StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING
            }
            : new StandardCopyOption[] {StandardCopyOption.ATOMIC_MOVE};
    try {
      Files.move(temporary, destination, options);
    } catch (AtomicMoveNotSupportedException ignored) {
      if (replace) {
        Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
      } else {
        Files.move(temporary, destination);
      }
    }
  }

  private static List<TableSpec> applicationTables() {
    List<TableSpec> tables = new ArrayList<>();
    tables.add(
        table(
            PostgresSchemaMigrator.MIGRATIONS_TABLE,
            "CREATE TABLE lifttrax_schema_migrations (version INTEGER PRIMARY KEY, name TEXT NOT NULL, applied_at TEXT NOT NULL)",
            "version",
            "name",
            "applied_at"));
    tables.add(
        table(
            "app_users",
            "CREATE TABLE app_users (id TEXT PRIMARY KEY, auth_user_id TEXT NOT NULL, email TEXT, username TEXT, created_at TEXT NOT NULL)",
            "id",
            "auth_user_id",
            "email",
            "username",
            "created_at"));
    tables.add(
        table(
            "lifter_profiles",
            "CREATE TABLE lifter_profiles (id TEXT PRIMARY KEY, owner_user_id TEXT NOT NULL, display_name TEXT NOT NULL, is_default INTEGER NOT NULL, created_at TEXT NOT NULL)",
            "id",
            "owner_user_id",
            "display_name",
            "is_default",
            "created_at"));
    tables.add(
        table(
            "exercise_catalog_entries",
            "CREATE TABLE exercise_catalog_entries (id TEXT PRIMARY KEY, owner_user_id TEXT NOT NULL, lifter_profile_id TEXT NOT NULL, name TEXT NOT NULL, region TEXT NOT NULL, main_lift TEXT, muscles TEXT NOT NULL, notes TEXT NOT NULL, enabled INTEGER NOT NULL, created_at TEXT NOT NULL)",
            "id",
            "owner_user_id",
            "lifter_profile_id",
            "name",
            "region",
            "main_lift",
            "muscles",
            "notes",
            "enabled",
            "created_at"));
    tables.add(
        table(
            "executions",
            "CREATE TABLE executions (web_execution_id INTEGER PRIMARY KEY, id TEXT NOT NULL, lifter_profile_id TEXT NOT NULL, catalog_entry_id TEXT NOT NULL, performed_on TEXT NOT NULL, warmup INTEGER NOT NULL, deload INTEGER NOT NULL, notes TEXT NOT NULL, created_at TEXT NOT NULL)",
            "web_execution_id",
            "id",
            "lifter_profile_id",
            "catalog_entry_id",
            "performed_on",
            "warmup",
            "deload",
            "notes",
            "created_at"));
    tables.add(
        table(
            "execution_sets",
            "CREATE TABLE execution_sets (id TEXT PRIMARY KEY, execution_id TEXT NOT NULL, set_index INTEGER NOT NULL, metric_kind TEXT NOT NULL, metric_a INTEGER NOT NULL, metric_b INTEGER, weight TEXT NOT NULL, rpe REAL)",
            "id",
            "execution_id",
            "set_index",
            "metric_kind",
            "metric_a",
            "metric_b",
            "weight",
            "rpe"));
    tables.add(
        table(
            "local_imports",
            "CREATE TABLE local_imports (id TEXT PRIMARY KEY, target_app_user_id TEXT NOT NULL, target_lifter_profile_id TEXT NOT NULL, source_kind TEXT NOT NULL, source_fingerprint TEXT NOT NULL, source_schema_version INTEGER NOT NULL, lift_count INTEGER NOT NULL, execution_count INTEGER NOT NULL, status TEXT NOT NULL, created_at TEXT NOT NULL)",
            "id",
            "target_app_user_id",
            "target_lifter_profile_id",
            "source_kind",
            "source_fingerprint",
            "source_schema_version",
            "lift_count",
            "execution_count",
            "status",
            "created_at"));
    tables.add(
        table(
            "local_import_records",
            "CREATE TABLE local_import_records (id TEXT PRIMARY KEY, target_lifter_profile_id TEXT NOT NULL, import_id TEXT NOT NULL, source_table TEXT NOT NULL, source_id TEXT NOT NULL, target_table TEXT NOT NULL, target_id TEXT NOT NULL)",
            "id",
            "target_lifter_profile_id",
            "import_id",
            "source_table",
            "source_id",
            "target_table",
            "target_id"));
    return List.copyOf(tables);
  }

  private static TableSpec table(String name, String createSql, String... columns) {
    return new TableSpec(name, List.of(columns), createSql);
  }

  record TableSpec(String name, List<String> columns, String createSql) {}

  interface BackupObserver {
    BackupObserver NONE = tableName -> {};

    void beforeTable(String tableName) throws Exception;
  }

  public record BackupResult(Path backupPath, String createdAtUtc, ValidationResult validation) {}

  public record ValidationResult(
      int formatVersion, int postgresSchemaVersion, Map<String, Long> rowCounts) {}
}
