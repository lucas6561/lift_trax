package com.lifttrax.workout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlannedWorkoutFileTest {
  private static final ObjectMapper JSON = new ObjectMapper();

  @Test
  void exportsGeneratedWaveToPlannedWorkoutFile() throws Exception {
    ConjugateWorkoutBuilderTest.FakeDb db = ConjugateWorkoutBuilderTest.FakeDb.withSeedData();
    var wave = new ConjugateWorkoutBuilder().getWave(1, db);

    PlannedWorkoutFile workoutFile =
        PlannedWorkoutExporter.fromWave(
            "Conjugate Wave", "conjugate", "2026-05-29T00:00:00Z", wave);

    assertEquals(PlannedWorkoutSchemaVersions.latest(), workoutFile.schemaVersion());
    assertEquals("Conjugate Wave Planned Workouts", workoutFile.metadata().name());
    assertEquals("wave-generation", workoutFile.source().kind());
    assertEquals(1, workoutFile.weeks().size());

    PlannedWorkoutFile.PlannedWorkoutWeek week = workoutFile.weeks().get(0);
    assertEquals(1, week.weekNumber());
    assertEquals("MONDAY", week.days().get(0).dayOfWeek());
    assertEquals("THURSDAY", week.days().get(2).dayOfWeek());

    PlannedWorkoutFile.PlannedWorkoutBlock maxEffort = week.days().get(0).blocks().get(1);
    assertEquals("max_effort", maxEffort.blockType());
    assertFalse(maxEffort.exercises().get(0).name().isBlank());
    assertEquals(1, maxEffort.exercises().get(0).plannedSets().get(0).reps());

    PlannedWorkoutFile.PlannedWorkoutBlock dynamic = week.days().get(2).blocks().get(1);
    assertEquals("dynamic_effort", dynamic.blockType());
    assertEquals(6, dynamic.exercises().get(0).plannedSets().size());
    assertEquals(60, dynamic.exercises().get(0).plannedSets().get(0).percent());
  }

  @Test
  void plannedWorkoutJsonRoundTripsTypedFiles() throws Exception {
    ConjugateWorkoutBuilderTest.FakeDb db = ConjugateWorkoutBuilderTest.FakeDb.withSeedData();
    var wave = new HypertrophyWorkoutBuilder().getWave(1, db);
    PlannedWorkoutFile workoutFile =
        PlannedWorkoutExporter.fromWave(
            "Hypertrophy Wave", "hypertrophy", "2026-05-29T00:00:00Z", wave);

    String json = PlannedWorkoutJson.writeString(workoutFile);
    PlannedWorkoutFile roundTrip = PlannedWorkoutJson.readString(json);

    assertEquals(workoutFile, roundTrip);
    assertTrue(json.contains("\"schemaVersion\""));
    assertTrue(json.contains("\"completedWorkouts\""));
  }

  @Test
  void plannedWorkoutJsonNormalizesCompactAiGeneratedSetTargets() throws Exception {
    PlannedWorkoutFile workoutFile =
        PlannedWorkoutJson.readString(
            """
            {
              "schemaVersion": 2,
              "metadata": {
                "name": "AI Hypertrophy",
                "description": "",
                "totalWeeks": 1,
                "tags": []
              },
              "source": {
                "kind": "ai",
                "generator": "ChatGPT",
                "programName": "AI Hypertrophy",
                "programSchemaVersion": 2,
                "generatedAt": "2026-06-01T00:00:00Z"
              },
              "weeks": [
                {
                  "weekNumber": 1,
                  "days": [
                    {
                      "dayOfWeek": "MONDAY",
                      "title": "Monday",
                      "blocks": [
                        {
                          "order": 1,
                          "title": "Warm-up",
                          "blockType": "warmup",
                          "warmup": true,
                          "exercises": [
                            {
                              "name": "General Mobility",
                              "region": "FULL_BODY",
                              "type": "MOBILITY",
                              "muscles": ["CORE"],
                              "plannedSets": [
                                {
                                  "setNumber": 1,
                                  "metricType": "timeSecs",
                                  "seconds": 300,
                                  "rpe": 5.0,
                                  "deload": false
                                }
                              ],
                              "notes": "",
                              "substitutionOptions": []
                            },
                            {
                              "name": "SSB Squat",
                              "region": "LOWER",
                              "type": "SQUAT",
                              "muscles": ["QUAD"],
                              "plannedSets": [
                                {
                                  "setNumber": 1,
                                  "metricType": "reps",
                                  "repsMin": 8,
                                  "repsMax": 10,
                                  "rpe": 8.0,
                                  "deload": false
                                }
                              ],
                              "notes": "",
                              "substitutionOptions": []
                            }
                          ],
                          "notes": []
                        }
                      ],
                      "notes": []
                    }
                  ]
                }
              ],
              "completedWorkouts": []
            }
            """);

    List<PlannedWorkoutFile.PlannedSetTarget> sets =
        workoutFile.weeks().get(0).days().get(0).blocks().get(0).exercises().stream()
            .flatMap(exercise -> exercise.plannedSets().stream())
            .toList();

    assertEquals("time_seconds", sets.get(0).metricType());
    assertEquals(300, sets.get(0).seconds());
    assertEquals("reps_range", sets.get(1).metricType());
    assertEquals(8, sets.get(1).repsMin());
    assertEquals(10, sets.get(1).repsMax());
  }

  @Test
  void plannedWorkoutMarkdownWorksForImportedWorkoutFiles() throws Exception {
    PlannedWorkoutFile workoutFile =
        PlannedWorkoutJson.readPath(
            Path.of("shared", "workouts", "examples", "conjugate-wave-v2.json"));

    String markdown =
        String.join("\n", PlannedWorkoutMarkdownWriter.createMarkdown(workoutFile, null));

    assertTrue(markdown.contains("# Example Conjugate Planned Wave"));
    assertTrue(markdown.contains("## Week 1"));
    assertTrue(markdown.contains("### Monday"));
    assertTrue(markdown.contains("#### Max Effort Single"));
    assertTrue(markdown.contains("**Back Squat** - 1 reps"));
    assertTrue(markdown.contains("Swap options: Safety Bar Squat, Front Squat"));
  }

  @Test
  void plannedWorkoutJsonRejectsUnsupportedSchemaVersion() {
    String invalid =
        """
            {
              "schemaVersion": 99,
              "metadata": {
                "name": "Bad",
                "description": "",
                "totalWeeks": 1,
                "tags": []
              },
              "source": {
                "kind": "import",
                "generator": "test",
                "programName": "Bad",
                "programSchemaVersion": null,
                "generatedAt": "2026-05-29T00:00:00Z"
              },
              "weeks": [
                {
                  "weekNumber": 1,
                  "days": [
                    {
                      "dayOfWeek": "MONDAY",
                      "title": "Monday",
                      "blocks": [
                        {
                          "order": 1,
                          "title": "Test",
                          "blockType": "single",
                          "rounds": null,
                          "warmup": false,
                          "exercises": [
                            {
                              "name": "Back Squat",
                              "region": "LOWER",
                              "type": "SQUAT",
                              "muscles": [],
                              "plannedSets": [],
                              "notes": "",
                              "substitutionOptions": []
                            }
                          ],
                          "notes": []
                        }
                      ],
                      "notes": []
                    }
                  ]
                }
              ],
              "completedWorkouts": []
            }
            """;

    JsonProcessingException exception =
        assertThrows(JsonProcessingException.class, () -> PlannedWorkoutJson.readString(invalid));

    assertTrue(
        exception
            .getOriginalMessage()
            .contains("Unsupported workout schemaVersion 99; supported versions: [1, 2]."));
  }

  @Test
  void plannedWorkoutJsonLoadsOlderAndLatestSchemaVersions() throws Exception {
    for (int schemaVersion : PlannedWorkoutSchemaVersions.supported()) {
      Path example =
          Path.of("shared", "workouts", "examples", "conjugate-wave-v" + schemaVersion + ".json");
      PlannedWorkoutFile workoutFile = PlannedWorkoutJson.readPath(example);

      assertEquals(schemaVersion, workoutFile.schemaVersion(), example.toString());
    }
  }

  @Test
  void workoutSchemaAssetsAreValidJsonAndDeclareSupportedShapes() throws Exception {
    for (int schemaVersion : PlannedWorkoutSchemaVersions.supported()) {
      Path schema =
          Path.of("shared", "workouts", "schema", "workout.schema.v" + schemaVersion + ".json");
      Path example =
          Path.of("shared", "workouts", "examples", "conjugate-wave-v" + schemaVersion + ".json");

      assertTrue(Files.isRegularFile(schema));
      assertTrue(Files.isRegularFile(example));
      assertFalse(JSON.readTree(schema.toFile()).isMissingNode());

      JsonNode root = JSON.readTree(example.toFile());
      assertEquals(schemaVersion, root.path("schemaVersion").asInt());
      assertTrue(root.path("metadata").has("name"));
      assertTrue(root.path("source").has("kind"));
      assertTrue(root.path("weeks").isArray());
      assertTrue(root.path("weeks").get(0).path("days").get(0).path("blocks").isArray());
      assertTrue(root.path("completedWorkouts").isArray());
    }
  }
}
