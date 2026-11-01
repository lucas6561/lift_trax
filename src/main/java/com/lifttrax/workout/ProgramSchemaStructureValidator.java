package com.lifttrax.workout;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Validates required program sections, weeks, and days. */
final class ProgramSchemaStructureValidator {
  private ProgramSchemaStructureValidator() {}

  static Dimensions validate(JsonNode root, List<ProgramSchemaValidationError> errors) {
    validateTopLevelSections(root, errors);
    int durationWeeks =
        requiredPositiveInt(
            root.path("program").path("durationWeeks"), "$.program.durationWeeks", errors);
    int daysPerWeek =
        requiredPositiveInt(
            root.path("program").path("daysPerWeek"), "$.program.daysPerWeek", errors);
    validateWeeks(root, durationWeeks, daysPerWeek, errors);
    return new Dimensions(durationWeeks, daysPerWeek);
  }

  private static void validateTopLevelSections(
      JsonNode root, List<ProgramSchemaValidationError> errors) {
    requireObject(root, "program", errors);
    requireObject(root, "exercisePools", errors);
    requireArray(root, "weeks", errors);
    optionalObject(root, "progressionRules", errors);
    optionalObject(root, "substitutionRules", errors);
    optionalObject(root, "dayTemplates", errors);
  }

  private static void requireObject(
      JsonNode root, String field, List<ProgramSchemaValidationError> errors) {
    JsonNode value = root.path(field);
    if (value.isMissingNode()) {
      addError(errors, "$." + field, "Missing required " + field + " section.");
      return;
    }
    if (!value.isObject()) {
      addError(errors, "$." + field, field + " must be an object.");
    }
  }

  private static void optionalObject(
      JsonNode root, String field, List<ProgramSchemaValidationError> errors) {
    JsonNode value = root.path(field);
    if (!value.isMissingNode() && !value.isObject()) {
      addError(errors, "$." + field, field + " must be an object when present.");
    }
  }

  private static void requireArray(
      JsonNode root, String field, List<ProgramSchemaValidationError> errors) {
    JsonNode value = root.path(field);
    if (value.isMissingNode()) {
      addError(errors, "$." + field, "Missing required " + field + " section.");
      return;
    }
    if (!value.isArray()) {
      addError(errors, "$." + field, field + " must be an array.");
    }
  }

  static int requiredPositiveInt(
      JsonNode value, String path, List<ProgramSchemaValidationError> errors) {
    if (!value.canConvertToInt()) {
      addError(errors, path, "Expected a positive integer.");
      return 0;
    }
    int number = value.asInt();
    if (number < 1) {
      addError(errors, path, "Expected a positive integer.");
      return 0;
    }
    return number;
  }

  private static void validateWeeks(
      JsonNode root,
      int durationWeeks,
      int daysPerWeek,
      List<ProgramSchemaValidationError> errors) {
    JsonNode weeks = root.path("weeks");
    if (!weeks.isArray()) {
      return;
    }
    if (weeks.isEmpty()) {
      addError(errors, "$.weeks", "Program must include at least one week.");
      return;
    }
    if (durationWeeks > 0 && weeks.size() != durationWeeks) {
      addError(
          errors,
          "$.weeks",
          "Expected "
              + durationWeeks
              + " weeks from $.program.durationWeeks, found "
              + weeks.size()
              + ".");
    }

    Set<Integer> seenWeekNumbers = new HashSet<>();
    for (int index = 0; index < weeks.size(); index++) {
      validateWeek(weeks.get(index), index, daysPerWeek, seenWeekNumbers, errors);
    }
    validateMissingWeekNumbers(durationWeeks, seenWeekNumbers, errors);
  }

  private static void validateWeek(
      JsonNode week,
      int index,
      int daysPerWeek,
      Set<Integer> seenWeekNumbers,
      List<ProgramSchemaValidationError> errors) {
    String weekPath = "$.weeks[" + index + "]";
    int weekNumber = requiredPositiveInt(week.path("weekNumber"), weekPath + ".weekNumber", errors);
    if (weekNumber > 0 && !seenWeekNumbers.add(weekNumber)) {
      addError(errors, weekPath + ".weekNumber", "Duplicate weekNumber " + weekNumber + ".");
    }

    JsonNode days = week.path("days");
    if (!days.isArray()) {
      addError(errors, weekPath + ".days", "Week days must be an array.");
      return;
    }
    if (daysPerWeek > 0 && days.size() != daysPerWeek) {
      addError(
          errors,
          weekPath + ".days",
          "Expected "
              + daysPerWeek
              + " days from $.program.daysPerWeek, found "
              + days.size()
              + ".");
    }
    validateDays(days, weekPath + ".days", errors);
  }

  private static void validateMissingWeekNumbers(
      int durationWeeks, Set<Integer> seenWeekNumbers, List<ProgramSchemaValidationError> errors) {
    if (durationWeeks < 1) {
      return;
    }

    List<Integer> missing = new ArrayList<>();
    for (int weekNumber = 1; weekNumber <= durationWeeks; weekNumber++) {
      if (!seenWeekNumbers.contains(weekNumber)) {
        missing.add(weekNumber);
      }
    }
    if (!missing.isEmpty()) {
      addError(errors, "$.weeks", "Missing weekNumber values: " + missing + ".");
    }
  }

  private static void validateDays(
      JsonNode days, String daysPath, List<ProgramSchemaValidationError> errors) {
    for (int index = 0; index < days.size(); index++) {
      JsonNode day = days.get(index);
      if (!day.hasNonNull("templateRef") && !day.hasNonNull("blocks")) {
        addError(
            errors, daysPath + "[" + index + "]", "Day must provide templateRef or inline blocks.");
      }
      if (day.has("blocks") && !day.path("blocks").isArray()) {
        addError(
            errors, daysPath + "[" + index + "].blocks", "Inline day blocks must be an array.");
      }
    }
  }

  private static void addError(
      List<ProgramSchemaValidationError> errors, String path, String message) {
    errors.add(new ProgramSchemaValidationError(path, message));
  }

  record Dimensions(int durationWeeks, int daysPerWeek) {}
}
