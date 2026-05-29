package com.lifttrax.workout;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProgramSchemaValidatorTest {
  private static final ProgramSchemaValidator VALIDATOR = new ProgramSchemaValidator();
  private static final List<Path> VALID_EXAMPLES =
      List.of(
          Path.of("shared", "programs", "examples", "conjugate-v1.json"),
          Path.of("shared", "programs", "examples", "hypertrophy-v1.json"));

  @Test
  void validExamplesPassValidation() {
    for (Path path : VALID_EXAMPLES) {
      ProgramSchemaValidationResult result = VALIDATOR.validate(path);

      assertTrue(result.isValid(), () -> path + " errors: " + result.errors());
    }
  }

  @Test
  void missingWeekReportsFieldLevelError() {
    ProgramSchemaValidationResult result =
        VALIDATOR.validate(
            Path.of("shared", "programs", "examples", "invalid", "missing-week-v1.json"));

    assertFalse(result.isValid());
    assertErrorContains(result, "$.weeks", "Missing weekNumber values: [2].");
  }

  @Test
  void missingExercisePoolReferenceReportsFieldLevelError() {
    ProgramSchemaValidationResult result =
        VALIDATOR.validate(
            Path.of("shared", "programs", "examples", "invalid", "missing-pool-reference-v1.json"));

    assertFalse(result.isValid());
    assertErrorContains(
        result,
        "$.dayTemplates.lowerDay.blocks[0].slots[0].target.poolRef",
        "Unknown poolRef \"notDefined\".");
  }

  @Test
  void impossibleProgressionReportsActionableErrors() {
    ProgramSchemaValidationResult result =
        VALIDATOR.validate(
            Path.of("shared", "programs", "examples", "invalid", "impossible-progression-v1.json"));

    assertFalse(result.isValid());
    assertErrorContains(
        result,
        "$.progressionRules.badWave.pattern[0].week",
        "Week 3 exceeds progression length 2.");
    assertErrorContains(
        result,
        "$.progressionRules.badWave.pattern[0].percentMin",
        "percentMin cannot be greater than percentMax.");
  }

  @Test
  void unsupportedSchemaVersionReportsFieldLevelError() {
    ProgramSchemaValidationResult result =
        VALIDATOR.validate(
            Path.of(
                "shared", "programs", "examples", "invalid", "unsupported-schema-version.json"));

    assertFalse(result.isValid());
    assertErrorContains(result, "$.schemaVersion", "Unsupported schemaVersion 99; expected 1.");
  }

  @Test
  void malformedJsonReportsParseError() {
    ProgramSchemaValidationResult result = VALIDATOR.validate("{");

    assertFalse(result.isValid());
    assertErrorContains(result, "$", "Program file is not valid JSON:");
  }

  private static void assertErrorContains(
      ProgramSchemaValidationResult result, String path, String message) {
    assertTrue(
        result.errors().stream()
            .anyMatch(error -> path.equals(error.path()) && error.message().contains(message)),
        () -> "Expected " + path + " containing " + message + " in " + result.errors());
  }
}
