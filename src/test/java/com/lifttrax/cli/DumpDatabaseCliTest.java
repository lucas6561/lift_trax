package com.lifttrax.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifttrax.db.SqliteDb;
import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.LiftRegion;
import com.lifttrax.models.LiftType;
import com.lifttrax.models.Muscle;
import com.lifttrax.models.SetMetric;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class DumpDatabaseCliTest {
  private static final ObjectMapper JSON = new ObjectMapper();

  @Test
  void liftsOnlyOptionPrintsLiftMetadataWithoutExecutions() throws Exception {
    Path tempDir = Files.createTempDirectory("lifttrax-dump-cli");
    Path dbPath = tempDir.resolve("lifts.db");

    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      db.addLift(
          "Front Squat",
          LiftRegion.LOWER,
          LiftType.SQUAT,
          List.of(Muscle.QUAD, Muscle.GLUTE),
          "heels elevated");
      db.addLiftExecution(
          "Front Squat",
          new LiftExecution(
              null,
              LocalDate.parse("2026-03-12"),
              List.of(new ExecutionSet(new SetMetric.Reps(5), "185 lb", 8.0f)),
              false,
              false,
              "smooth"));
    }

    ByteArrayOutputStream output = new ByteArrayOutputStream();
    PrintStream originalOut = System.out;
    try {
      System.setOut(new PrintStream(output));
      try (SqliteDb db = new SqliteDb(dbPath.toString())) {
        DumpDatabaseCli.dump(db, true);
      }
    } finally {
      System.setOut(originalOut);
    }

    String text = output.toString();
    assertTrue(text.contains("Front Squat (LOWER) [SQUAT] [QUAD, GLUTE] - heels elevated"));
    assertFalse(text.contains("2026-03-12:"));
    assertFalse(text.contains("(no executions)"));
  }

  @Test
  void liftsOnlyOmitsDisabledLiftsUnlessExplicitlyIncluded() throws Exception {
    Path dbPath = Files.createTempDirectory("lifttrax-dump-disabled").resolve("lifts.db");
    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      db.addLift("Enabled Press", LiftRegion.UPPER, LiftType.BENCH_PRESS, List.of(), "");
      db.addLift("Disabled Press", LiftRegion.UPPER, LiftType.BENCH_PRESS, List.of(), "");
      db.setLiftEnabled("Disabled Press", false);
    }

    String defaultOutput;
    String completeOutput;
    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      defaultOutput = captureDump(db, false);
      completeOutput = captureDump(db, true);
    }

    assertTrue(defaultOutput.contains("Enabled Press"));
    assertFalse(defaultOutput.contains("Disabled Press"));
    assertTrue(completeOutput.contains("Enabled Press"));
    assertTrue(completeOutput.contains("Disabled Press"));
  }

  @Test
  void jsonExecutionDumpIncludesAllDatesAndMetricShapes() throws Exception {
    Path dbPath = Files.createTempDirectory("lifttrax-execution-json").resolve("lifts.db");
    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      db.addLift(
          "Front Squat",
          LiftRegion.LOWER,
          LiftType.SQUAT,
          List.of(Muscle.QUAD, Muscle.GLUTE),
          "heels elevated");
      db.addLift("Farmer Carry", LiftRegion.LOWER, null, List.of(Muscle.FOREARM), "");
      db.addLiftExecution(
          "Farmer Carry",
          new LiftExecution(
              null,
              LocalDate.parse("2026-01-03"),
              List.of(new ExecutionSet(new SetMetric.DistanceFeet(100), "180 lb", 7.5f)),
              false,
              false,
              null));
      db.addLiftExecution(
          "Front Squat",
          new LiftExecution(
              null,
              LocalDate.parse("2026-02-04"),
              List.of(
                  new ExecutionSet(new SetMetric.Reps(5), "185 lb", 8.0f),
                  new ExecutionSet(new SetMetric.RepsLr(4, 3), "none", null),
                  new ExecutionSet(new SetMetric.RepsRange(8, 12), null, null),
                  new ExecutionSet(new SetMetric.TimeSecs(45), "bodyweight", null)),
              true,
              true,
              "smooth"));
    }

    JsonNode root;
    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      root = JSON.readTree(captureExecutionDump(db, null, null, ExecutionDumpWriter.Format.JSON));
    }

    assertEquals(1, root.path("schemaVersion").asInt());
    assertTrue(root.path("dateRange").path("from").isNull());
    assertTrue(root.path("dateRange").path("to").isNull());
    assertEquals(2, root.path("executionCount").asInt());
    assertEquals("2026-01-03", root.path("executions").path(0).path("date").asText());
    JsonNode squat = root.path("executions").path(1);
    assertEquals("Front Squat", squat.path("lift").path("name").asText());
    assertEquals("LOWER", squat.path("lift").path("region").asText());
    assertEquals("SQUAT", squat.path("lift").path("main").asText());
    assertEquals("QUAD", squat.path("lift").path("muscles").path(0).asText());
    assertTrue(squat.path("warmup").asBoolean());
    assertTrue(squat.path("deload").asBoolean());
    assertEquals("smooth", squat.path("notes").asText());
    assertEquals("reps", squat.path("sets").path(0).path("metric").path("kind").asText());
    assertEquals(5, squat.path("sets").path(0).path("metric").path("reps").asInt());
    assertEquals("reps_lr", squat.path("sets").path(1).path("metric").path("kind").asText());
    assertEquals("reps_range", squat.path("sets").path(2).path("metric").path("kind").asText());
    assertEquals("time_seconds", squat.path("sets").path(3).path("metric").path("kind").asText());
  }

  @Test
  void executionDateRangeIsInclusive() throws Exception {
    Path dbPath = executionRangeFixture();

    JsonNode root;
    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      root =
          JSON.readTree(
              captureExecutionDump(
                  db,
                  LocalDate.parse("2026-02-01"),
                  LocalDate.parse("2026-02-28"),
                  ExecutionDumpWriter.Format.JSON));
    }

    assertEquals("2026-02-01", root.path("dateRange").path("from").asText());
    assertEquals("2026-02-28", root.path("dateRange").path("to").asText());
    assertEquals(2, root.path("executionCount").asInt());
    assertEquals("2026-02-01", root.path("executions").path(0).path("date").asText());
    assertEquals("2026-02-28", root.path("executions").path(1).path("date").asText());
  }

  @Test
  void humanExecutionDumpShowsReadableRangeSetsTagsAndNotes() throws Exception {
    Path dbPath = executionRangeFixture();

    String text;
    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      text =
          captureExecutionDump(
              db, LocalDate.parse("2026-02-01"), null, ExecutionDumpWriter.Format.HUMAN);
    }

    assertTrue(text.contains("Execution dump"));
    assertTrue(text.contains("Date range: from 2026-02-01"));
    assertTrue(text.contains("Executions: 2"));
    assertTrue(text.contains("2026-02-01 | Front Squat | execution"));
    assertTrue(text.contains("Set 1: 5 reps @ 185 lb RPE 8.0"));
    assertTrue(text.contains("Tags: (warm-up)"));
    assertTrue(text.contains("Notes: boundary"));
    assertFalse(text.contains("2026-01-31 |"));
  }

  @Test
  void emptyHumanExecutionDumpExplainsUpperBound() throws Exception {
    Path dbPath = executionRangeFixture();

    String text;
    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      text =
          captureExecutionDump(
              db, null, LocalDate.parse("2026-01-01"), ExecutionDumpWriter.Format.HUMAN);
    }

    assertTrue(text.contains("Date range: through 2026-01-01"));
    assertTrue(text.contains("Executions: 0"));
    assertTrue(text.contains("No executions found."));
  }

  @Test
  void executionDumpSchemasAreValidMatchingJsonDocuments() throws Exception {
    JsonNode versioned =
        JSON.readTree(
            Files.readString(
                Path.of("shared", "executions", "schema", "execution-dump.schema.v1.json")));
    JsonNode latest =
        JSON.readTree(
            Files.readString(
                Path.of("shared", "executions", "schema", "execution-dump.schema.latest.json")));

    assertEquals(versioned, latest);
    assertEquals(1, versioned.path("properties").path("schemaVersion").path("const").asInt());
    assertTrue(versioned.path("$defs").path("metric").path("oneOf").isArray());
  }

  private static String captureDump(SqliteDb db, boolean includeDisabled) throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    PrintStream originalOut = System.out;
    try {
      System.setOut(new PrintStream(output));
      DumpDatabaseCli.dump(db, true, includeDisabled);
    } finally {
      System.setOut(originalOut);
    }
    return output.toString();
  }

  private static String captureExecutionDump(
      SqliteDb db, LocalDate from, LocalDate to, ExecutionDumpWriter.Format format)
      throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ExecutionDumpWriter.write(db, from, to, format, new PrintStream(output));
    return output.toString();
  }

  private static Path executionRangeFixture() throws Exception {
    Path dbPath = Files.createTempDirectory("lifttrax-execution-range").resolve("lifts.db");
    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      db.addLift("Front Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(), "");
      addExecution(db, "2026-01-31", false, "before");
      addExecution(db, "2026-02-01", true, "boundary");
      addExecution(db, "2026-02-28", false, "boundary");
    }
    return dbPath;
  }

  private static void addExecution(SqliteDb db, String date, boolean warmup, String notes)
      throws Exception {
    db.addLiftExecution(
        "Front Squat",
        new LiftExecution(
            null,
            LocalDate.parse(date),
            List.of(new ExecutionSet(new SetMetric.Reps(5), "185 lb", 8.0f)),
            warmup,
            false,
            notes));
  }
}
