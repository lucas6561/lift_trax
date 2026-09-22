package com.lifttrax.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lifttrax.db.HostedPostgresTrainingDataStoreProvider;
import com.lifttrax.workout.PlannedWorkoutFile;
import com.lifttrax.workout.PlannedWorkoutJson;
import java.sql.Connection;
import java.sql.Date;
import java.time.LocalDate;

/** Synthetic history inserted in batches so setup stays outside the route timing budget. */
final class RoutePerformanceFixture {
  static final String USER = "performance-owner";
  static final int LIFTS = 100;
  static final int EXECUTIONS_PER_LIFT = 120;
  static final int SETS_PER_EXECUTION = 4;
  static final String PLAN_NAME = "Performance smoke wave";

  private RoutePerformanceFixture() {}

  static void seed(Connection connection, HostedPostgresTrainingDataStoreProvider provider)
      throws Exception {
    provider.forUser(USER);
    String profileId;
    String ownerId;
    try (var query =
        connection.prepareStatement(
            "SELECT p.id, p.owner_user_id FROM lifter_profiles p "
                + "JOIN app_users u ON u.id = p.owner_user_id WHERE u.auth_user_id = ?")) {
      query.setString(1, USER);
      try (var rows = query.executeQuery()) {
        assertTrue(rows.next());
        profileId = rows.getString("id");
        ownerId = rows.getString("owner_user_id");
      }
    }
    connection.setAutoCommit(false);
    try (var lifts =
            connection.prepareStatement(
                "INSERT INTO exercise_catalog_entries "
                    + "(id, owner_user_id, lifter_profile_id, name, region, main_lift) "
                    + "VALUES (?, ?, ?, ?, 'LOWER', 'SQUAT')");
        var executions =
            connection.prepareStatement(
                "INSERT INTO executions (id, lifter_profile_id, catalog_entry_id, performed_on, warmup, deload) "
                    + "VALUES (?, ?, ?, ?, ?, ?)");
        var sets =
            connection.prepareStatement(
                "INSERT INTO execution_sets (id, execution_id, set_index, metric_kind, metric_a, weight, rpe) "
                    + "VALUES (?, ?, ?, 'reps', 5, '185 lb', 8)")) {
      LocalDate today = LocalDate.now();
      for (int lift = 0; lift < LIFTS; lift++) {
        String liftId = "perf-lift-" + lift;
        lifts.setString(1, liftId);
        lifts.setString(2, ownerId);
        lifts.setString(3, profileId);
        lifts.setString(4, liftName(lift));
        lifts.addBatch();
      }
      lifts.executeBatch();
      for (int lift = 0; lift < LIFTS; lift++) {
        for (int visit = 0; visit < EXECUTIONS_PER_LIFT; visit++) {
          String executionId = "perf-execution-" + lift + "-" + visit;
          executions.setString(1, executionId);
          executions.setString(2, profileId);
          executions.setString(3, "perf-lift-" + lift);
          executions.setDate(4, Date.valueOf(today.minusDays(visit * 7L + lift % 7)));
          executions.setBoolean(5, visit % 10 == 9);
          executions.setBoolean(6, visit % 12 == 11);
          executions.addBatch();
          for (int set = 0; set < SETS_PER_EXECUTION; set++) {
            sets.setString(1, "perf-set-" + lift + "-" + visit + "-" + set);
            sets.setString(2, executionId);
            sets.setInt(3, set);
            sets.addBatch();
          }
        }
        executions.executeBatch();
        sets.executeBatch();
      }
      connection.commit();
    } catch (Exception e) {
      connection.rollback();
      throw e;
    } finally {
      connection.setAutoCommit(true);
    }
    assertCount(connection, "exercise_catalog_entries", LIFTS);
    assertCount(connection, "executions", LIFTS * EXECUTIONS_PER_LIFT);
    assertCount(connection, "execution_sets", LIFTS * EXECUTIONS_PER_LIFT * SETS_PER_EXECUTION);
  }

  static String workoutJson() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode root = mapper.createObjectNode();
    root.put("schemaVersion", PlannedWorkoutFile.LATEST_SCHEMA_VERSION);
    root.putObject("metadata").put("name", PLAN_NAME).put("totalWeeks", 4);
    root.putObject("source")
        .put("kind", "test-fixture")
        .put("generator", "route-performance")
        .put("programName", PLAN_NAME)
        .put("generatedAt", "2026-09-22T00:00:00Z");
    var weeks = root.putArray("weeks");
    String[] weekdays = {"MONDAY", "WEDNESDAY", "FRIDAY"};
    for (int week = 1; week <= 4; week++) {
      var days = weeks.addObject().put("weekNumber", week).putArray("days");
      for (int day = 0; day < weekdays.length; day++) {
        var blocks =
            days.addObject()
                .put("dayOfWeek", weekdays[day])
                .put("title", "Training day " + (day + 1))
                .putArray("blocks");
        for (int exercise = 0; exercise < 6; exercise++) {
          var planned =
              blocks
                  .addObject()
                  .put("order", exercise + 1)
                  .put("title", "Working sets " + (exercise + 1))
                  .put("blockType", "straight_sets")
                  .putArray("exercises")
                  .addObject();
          planned.put("name", liftName(day * 6 + exercise));
          var sets = planned.putArray("plannedSets");
          for (int set = 1; set <= SETS_PER_EXECUTION; set++) {
            sets.addObject().put("setNumber", set).put("metricType", "reps").put("reps", 5);
          }
        }
      }
    }
    return PlannedWorkoutJson.writeString(PlannedWorkoutJson.readString(root.toString()));
  }

  static String liftName(int index) {
    return "Performance Lift " + index;
  }

  private static void assertCount(Connection connection, String table, int expected)
      throws Exception {
    try (var statement = connection.createStatement();
        var rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
      assertTrue(rows.next());
      assertEquals(expected, rows.getInt(1), "Performance fixture row count: " + table);
    }
  }
}
