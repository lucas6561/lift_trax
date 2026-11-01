package com.lifttrax.db;

import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.LiftExecution;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Loads executions for several catalog lifts in one hosted query. */
final class HostedExecutionBatchReader {
  private HostedExecutionBatchReader() {}

  static Map<String, List<LiftExecution>> load(
      Connection connection, String appUserId, String lifterProfileId, Collection<String> liftNames)
      throws Exception {
    List<String> requestedNames = new ArrayList<>(new LinkedHashSet<>(liftNames));
    Map<String, List<LiftExecution>> executionsByLift = emptyResults(requestedNames);
    if (requestedNames.isEmpty()) {
      return executionsByLift;
    }

    try (PreparedStatement statement = connection.prepareStatement(query(requestedNames.size()))) {
      bind(statement, appUserId, lifterProfileId, requestedNames);
      Map<String, Map<Integer, ExecutionRows>> rowsByLift = read(statement);
      for (Map.Entry<String, Map<Integer, ExecutionRows>> entry : rowsByLift.entrySet()) {
        executionsByLift.put(
            entry.getKey(),
            entry.getValue().values().stream().map(ExecutionRows::toExecution).toList());
      }
      return executionsByLift;
    }
  }

  private static Map<String, List<LiftExecution>> emptyResults(List<String> names) {
    Map<String, List<LiftExecution>> results = new LinkedHashMap<>();
    for (String name : names) {
      results.put(name, new ArrayList<>());
    }
    return results;
  }

  private static String query(int requestedCount) {
    String placeholders = String.join(", ", Collections.nCopies(requestedCount, "?"));
    return """
        SELECT c.name, e.web_execution_id, e.id, e.performed_on, e.warmup, e.deload,
            e.notes, es.metric_kind, es.metric_a, es.metric_b, es.weight, es.rpe, es.missed
        FROM executions e
        JOIN exercise_catalog_entries c ON c.id = e.catalog_entry_id
        LEFT JOIN execution_sets es ON es.execution_id = e.id
        WHERE e.lifter_profile_id = ? AND c.lifter_profile_id = ?
            AND c.owner_user_id = ? AND c.name IN (%s)
        ORDER BY c.name, e.performed_on DESC, e.web_execution_id DESC, es.set_index
        """
        .formatted(placeholders);
  }

  private static void bind(
      PreparedStatement statement, String appUserId, String lifterProfileId, List<String> names)
      throws Exception {
    statement.setString(1, lifterProfileId);
    statement.setString(2, lifterProfileId);
    statement.setString(3, appUserId);
    for (int index = 0; index < names.size(); index++) {
      statement.setString(index + 4, names.get(index));
    }
  }

  private static Map<String, Map<Integer, ExecutionRows>> read(PreparedStatement statement)
      throws Exception {
    Map<String, Map<Integer, ExecutionRows>> rowsByLift = new LinkedHashMap<>();
    try (ResultSet rows = statement.executeQuery()) {
      while (rows.next()) {
        append(rowsByLift, rows);
      }
    }
    return rowsByLift;
  }

  private static void append(Map<String, Map<Integer, ExecutionRows>> rowsByLift, ResultSet result)
      throws Exception {
    String liftName = result.getString("name");
    int executionId = result.getInt("web_execution_id");
    Map<Integer, ExecutionRows> rowsByExecution =
        rowsByLift.computeIfAbsent(liftName, ignored -> new LinkedHashMap<>());
    ExecutionRows rows =
        rowsByExecution.computeIfAbsent(executionId, ignored -> execution(result, executionId));
    if (result.getString("metric_kind") != null) {
      rows.sets().add(HostedExecutionJdbc.mapExecutionSet(result));
    }
  }

  private static ExecutionRows execution(ResultSet result, int executionId) {
    try {
      return new ExecutionRows(
          executionId,
          HostedExecutionJdbc.parseDate(result.getObject("performed_on")),
          result.getBoolean("warmup"),
          result.getBoolean("deload"),
          result.getString("notes"),
          new ArrayList<>());
    } catch (Exception error) {
      throw new IllegalStateException("Could not map hosted execution.", error);
    }
  }

  private record ExecutionRows(
      int id,
      LocalDate date,
      boolean warmup,
      boolean deload,
      String notes,
      List<ExecutionSet> sets) {
    LiftExecution toExecution() {
      return new LiftExecution(id, date, List.copyOf(sets), warmup, deload, notes);
    }
  }
}
