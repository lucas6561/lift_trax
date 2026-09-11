package com.lifttrax.workout;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifttrax.models.LiftRegion;
import com.lifttrax.models.LiftType;
import com.lifttrax.models.Muscle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GoldenWorkoutOutputTest {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Path FIXTURES = Path.of("src/test/resources/golden/workouts");
  private static final Path ACTUAL = Path.of("build/golden/workouts");

  @ParameterizedTest
  @ValueSource(strings = {"conjugate", "hypertrophy"})
  void generatedWorkoutMatchesReviewedFixture(String generator) throws Exception {
    String actual = generate(generator);
    assertEquals(
        JSON.readTree(actual),
        JSON.readTree(generate(generator)),
        "Fresh generation must be deterministic");

    // Always write a review candidate outside source control, never replace the expected fixture.
    Files.createDirectories(ACTUAL);
    Path candidate = ACTUAL.resolve(generator + ".json");
    Files.writeString(candidate, actual + "\n");

    Path expected = FIXTURES.resolve(generator + ".json");
    JsonNode expectedJson = JSON.readTree(Files.readString(expected));
    JsonNode actualJson = JSON.readTree(actual);
    assertEquals(
        expectedJson,
        actualJson,
        "Workout output changed. Review "
            + candidate
            + " against "
            + expected
            + "; see src/test/resources/golden/workouts/README.md");
    assertEquals(
        PlannedWorkoutJson.readString(actual),
        PlannedWorkoutJson.readPath(expected),
        "The reviewed output must remain importable as the same planned workout");
  }

  private static String generate(String generator) throws Exception {
    // This fixed, ordered catalog contains no execution dates or machine-local data.
    var db = ConjugateWorkoutBuilderTest.FakeDb.withParitySeedData();
    db.add("Chest Fly", LiftRegion.UPPER, LiftType.ACCESSORY, List.of(Muscle.CHEST));
    WorkoutBuilder builder =
        generator.equals("conjugate")
            ? new ConjugateWorkoutBuilder(
                new DefaultMaxEffortPlanSource(),
                catalog -> DynamicLifts.fromDatabase(catalog, false, RandomSupport.DETERMINISTIC),
                RandomSupport.DETERMINISTIC)
            : new HypertrophyWorkoutBuilder();
    return PlannedWorkoutJson.writeString(
        PlannedWorkoutExporter.fromWave(
            "Golden " + generator + " wave",
            generator,
            "2026-09-11T00:00:00Z",
            builder.getWave(2, db)));
  }
}
