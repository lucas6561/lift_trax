package com.lifttrax.db;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Creates and restores plain SQLite database backups for LiftTrax. */
public final class DatabaseBackupService {
  private static final DateTimeFormatter BACKUP_TIMESTAMP =
      DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.ROOT).withZone(ZoneOffset.UTC);
  private static final Set<String> REQUIRED_TABLES = Set.of("lifts", "lift_records");

  private DatabaseBackupService() {}

  public static BackupResult createBackup(Path dbPath) throws SQLException {
    Path source = normalizedPath(dbPath);
    ValidationResult validation = validateLiftTraxDatabase(source);
    Path backupPath = defaultBackupPath(source, "backup");
    return createBackup(source, backupPath, validation);
  }

  public static BackupResult createBackup(Path dbPath, Path backupPath) throws SQLException {
    Path source = normalizedPath(dbPath);
    ValidationResult validation = validateLiftTraxDatabase(source);
    return createBackup(source, normalizedPath(backupPath), validation);
  }

  public static RestoreResult restoreBackup(Path backupPath, Path dbPath, boolean confirmOverwrite)
      throws SQLException {
    Path source = normalizedPath(backupPath);
    Path target = normalizedPath(dbPath);
    ValidationResult validation = validateLiftTraxDatabase(source);
    boolean targetExists = Files.exists(target);
    if (targetExists && !confirmOverwrite) {
      throw new IllegalArgumentException(
          "Refusing to overwrite "
              + target
              + ". Re-run restore with --confirm-overwrite after making sure this is the right"
              + " backup.");
    }

    Path preRestoreBackup = null;
    try {
      Path parent = target.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      if (targetExists) {
        preRestoreBackup = defaultBackupPath(target, "pre-restore");
        Path preRestoreParent = preRestoreBackup.getParent();
        if (preRestoreParent != null) {
          Files.createDirectories(preRestoreParent);
        }
        Files.copy(target, preRestoreBackup, StandardCopyOption.COPY_ATTRIBUTES);
      }
      Files.copy(
          source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to restore LiftTrax database backup", e);
    }
    return new RestoreResult(source, target, preRestoreBackup, validation);
  }

  public static ValidationResult validateLiftTraxDatabase(Path dbPath) throws SQLException {
    Path database = normalizedPath(dbPath);
    if (!Files.isRegularFile(database)) {
      throw new IllegalArgumentException("Database file not found: " + database);
    }

    try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database)) {
      Set<String> tables = tableNames(connection);
      Set<String> missing = new LinkedHashSet<>(REQUIRED_TABLES);
      missing.removeAll(tables);
      if (!missing.isEmpty()) {
        throw new IllegalArgumentException(
            "Not a LiftTrax database. Missing table(s): " + String.join(", ", missing));
      }

      int schemaVersion = activeVersion(connection);
      int currentVersion = SqlSchemaVersion.current();
      if (schemaVersion > currentVersion) {
        throw new IllegalArgumentException(
            "Database schema version "
                + schemaVersion
                + " is newer than supported version "
                + currentVersion);
      }
      return new ValidationResult(database, schemaVersion, tables);
    }
  }

  private static BackupResult createBackup(
      Path source, Path backupPath, ValidationResult validation) {
    try {
      Path parent = backupPath.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Files.copy(source, backupPath, StandardCopyOption.COPY_ATTRIBUTES);
      return new BackupResult(source, backupPath, validation);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to create LiftTrax database backup", e);
    }
  }

  private static Path defaultBackupPath(Path dbPath, String reason) {
    Path parent = dbPath.getParent();
    Path backupDir = parent == null ? Path.of("backups") : parent.resolve("backups");
    String stem = dbPath.getFileName().toString();
    int extensionAt = stem.toLowerCase(Locale.ROOT).lastIndexOf(".db");
    if (extensionAt > 0) {
      stem = stem.substring(0, extensionAt);
    }
    String timestamp = BACKUP_TIMESTAMP.format(ZonedDateTime.now(ZoneOffset.UTC));
    return uniquePath(backupDir.resolve(stem + "-" + reason + "-" + timestamp + ".db"));
  }

  private static Path uniquePath(Path preferredPath) {
    if (!Files.exists(preferredPath)) {
      return preferredPath;
    }
    String fileName = preferredPath.getFileName().toString();
    String stem = fileName;
    String extension = "";
    int extensionAt = fileName.lastIndexOf('.');
    if (extensionAt > 0) {
      stem = fileName.substring(0, extensionAt);
      extension = fileName.substring(extensionAt);
    }
    for (int suffix = 2; ; suffix++) {
      Path candidate = preferredPath.resolveSibling(stem + "-" + suffix + extension);
      if (!Files.exists(candidate)) {
        return candidate;
      }
    }
  }

  private static Path normalizedPath(Path path) {
    return path.toAbsolutePath().normalize();
  }

  private static Set<String> tableNames(Connection connection) throws SQLException {
    Set<String> names = new LinkedHashSet<>();
    String sql = "SELECT name FROM sqlite_master WHERE type = 'table'";
    try (PreparedStatement statement = connection.prepareStatement(sql);
        ResultSet rs = statement.executeQuery()) {
      while (rs.next()) {
        names.add(rs.getString("name"));
      }
    }
    return names;
  }

  private static int activeVersion(Connection connection) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement("PRAGMA user_version");
        ResultSet rs = statement.executeQuery()) {
      return rs.next() ? rs.getInt(1) : 0;
    }
  }

  public record BackupResult(Path source, Path backupPath, ValidationResult validation) {}

  public record RestoreResult(
      Path backupPath, Path restoredPath, Path preRestoreBackupPath, ValidationResult validation) {}

  public record ValidationResult(Path dbPath, int schemaVersion, Set<String> tableNames) {}
}
