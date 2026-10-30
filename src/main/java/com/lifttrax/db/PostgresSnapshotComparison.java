package com.lifttrax.db;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** Compares validated snapshots without writing to either snapshot or connecting to Postgres. */
public final class PostgresSnapshotComparison {
  private PostgresSnapshotComparison() {}

  public static void requireIdentical(Path before, Path after) throws Exception {
    var baseline = PostgresSqliteBackupService.validate(before);
    var current = PostgresSqliteBackupService.validate(after);
    if (!baseline.rowCounts().equals(current.rowCounts())) {
      throw new IllegalStateException("Application table counts differ between snapshots.");
    }
    try (Connection first = openReadOnly(before);
        Connection second = openReadOnly(after)) {
      for (String table : baseline.rowCounts().keySet()) {
        compareTable(first, second, table);
      }
    }
  }

  private static Connection openReadOnly(Path path) throws SQLException {
    return DriverManager.getConnection(
        "jdbc:sqlite:" + path.toAbsolutePath().normalize().toUri() + "?mode=ro");
  }

  private static void compareTable(Connection first, Connection second, String table)
      throws SQLException {
    int count = columnCount(first, table);
    try (Statement firstStatement = first.createStatement();
        Statement secondStatement = second.createStatement()) {
      String order =
          IntStream.rangeClosed(1, count)
              .mapToObj(Integer::toString)
              .collect(Collectors.joining(","));
      String query = "SELECT * FROM " + table + " ORDER BY " + order;
      try (ResultSet before = firstStatement.executeQuery(query);
          ResultSet after = secondStatement.executeQuery(query)) {
        while (before.next()) {
          if (!after.next()) {
            throw changed(table);
          }
          for (int index = 1; index <= count; index++) {
            if (!Objects.equals(before.getString(index), after.getString(index))) {
              throw changed(table);
            }
          }
        }
        if (after.next()) {
          throw changed(table);
        }
      }
    }
  }

  private static int columnCount(Connection connection, String table) throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet columns = statement.executeQuery("SELECT * FROM " + table + " LIMIT 0")) {
      return columns.getMetaData().getColumnCount();
    }
  }

  private static IllegalStateException changed(String table) {
    // Report the affected table, never the private account data or password hashes.
    return new IllegalStateException("Application rows differ in snapshot table: " + table);
  }
}
