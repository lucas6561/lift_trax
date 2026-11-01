package com.lifttrax.workout;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Validates progression, range, and percentage rules. */
final class ProgramSchemaRuleValidator {
  private ProgramSchemaRuleValidator() {}

  static void validate(
      JsonNode root, int durationWeeks, List<ProgramSchemaValidationError> errors) {
    validateProgressions(root, durationWeeks, errors);
    validateRangePairs(root, "$", errors);
    validatePercentageReferences(root, errors);
  }

  private static void validateProgressions(
      JsonNode root, int durationWeeks, List<ProgramSchemaValidationError> errors) {
    JsonNode rules = root.path("progressionRules");
    if (!rules.isObject()) {
      return;
    }

    Iterator<Map.Entry<String, JsonNode>> fields = rules.fields();
    while (fields.hasNext()) {
      Map.Entry<String, JsonNode> field = fields.next();
      validateProgressionRule(
          field.getValue(), "$.progressionRules." + field.getKey(), durationWeeks, errors);
    }
  }

  private static void validateProgressionRule(
      JsonNode rule, String path, int durationWeeks, List<ProgramSchemaValidationError> errors) {
    int cycleWeeks = optionalPositiveInt(rule.path("cycleWeeks"), path + ".cycleWeeks", errors);
    int weekLimit = cycleWeeks > 0 ? cycleWeeks : durationWeeks;
    JsonNode pattern = rule.path("pattern");
    if (!pattern.isArray()) {
      return;
    }
    for (int index = 0; index < pattern.size(); index++) {
      validateProgressionStep(
          pattern.get(index), path + ".pattern[" + index + "]", weekLimit, errors);
    }
  }

  private static void validateProgressionStep(
      JsonNode step, String path, int weekLimit, List<ProgramSchemaValidationError> errors) {
    validateWeekLimit(step.path("week"), path + ".week", weekLimit, errors);
    JsonNode weeks = step.path("weeks");
    if (!weeks.isArray()) {
      return;
    }
    for (int index = 0; index < weeks.size(); index++) {
      validateWeekLimit(weeks.get(index), path + ".weeks[" + index + "]", weekLimit, errors);
    }
  }

  private static void validateWeekLimit(
      JsonNode value, String path, int weekLimit, List<ProgramSchemaValidationError> errors) {
    if (value.isMissingNode()) {
      return;
    }
    int week = ProgramSchemaStructureValidator.requiredPositiveInt(value, path, errors);
    if (weekLimit > 0 && week > weekLimit) {
      addError(errors, path, "Week " + week + " exceeds progression length " + weekLimit + ".");
    }
  }

  private static void validateRangePairs(
      JsonNode node, String path, List<ProgramSchemaValidationError> errors) {
    if (node.isObject()) {
      validateRangePair(node, path, "percentMin", "percentMax", errors);
      validateRangePair(node, path, "min", "max", errors);

      Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> field = fields.next();
        validateRangePairs(field.getValue(), path + "." + field.getKey(), errors);
      }
      return;
    }
    if (node.isArray()) {
      for (int index = 0; index < node.size(); index++) {
        validateRangePairs(node.get(index), path + "[" + index + "]", errors);
      }
    }
  }

  private static void validatePercentageReferences(
      JsonNode node, List<ProgramSchemaValidationError> errors) {
    validatePercentageReferences(node, "$", node.path("schemaVersion").asInt(), errors);
  }

  private static void validatePercentageReferences(
      JsonNode node, String path, int schemaVersion, List<ProgramSchemaValidationError> errors) {
    if (node.isObject()) {
      JsonNode reference = node.path("percentOf");
      if (!reference.isMissingNode()) {
        if (schemaVersion < 3) {
          addError(errors, path + ".percentOf", "percentOf requires program schemaVersion 3.");
        }
        if (!reference.isTextual() || reference.asText().isBlank()) {
          addError(errors, path + ".percentOf", "percentOf must name a lift when present.");
        }
        boolean hasPercentage =
            node.path("percent").isNumber()
                || node.path("percentMin").isNumber()
                || node.path("percentMax").isNumber();
        if (!hasPercentage) {
          addError(
              errors,
              path + ".percentOf",
              "percentOf requires percent, percentMin, or percentMax on the same object.");
        }
      }

      Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> field = fields.next();
        validatePercentageReferences(
            field.getValue(), path + "." + field.getKey(), schemaVersion, errors);
      }
      return;
    }
    if (node.isArray()) {
      for (int index = 0; index < node.size(); index++) {
        validatePercentageReferences(
            node.get(index), path + "[" + index + "]", schemaVersion, errors);
      }
    }
  }

  private static void validateRangePair(
      JsonNode node,
      String path,
      String minField,
      String maxField,
      List<ProgramSchemaValidationError> errors) {
    JsonNode min = node.path(minField);
    JsonNode max = node.path(maxField);
    if (!min.canConvertToInt() || !max.canConvertToInt()) {
      return;
    }
    if (min.asInt() > max.asInt()) {
      addError(
          errors, path + "." + minField, minField + " cannot be greater than " + maxField + ".");
    }
  }

  private static int optionalPositiveInt(
      JsonNode value, String path, List<ProgramSchemaValidationError> errors) {
    if (value.isMissingNode()) {
      return 0;
    }
    return ProgramSchemaStructureValidator.requiredPositiveInt(value, path, errors);
  }

  private static void addError(
      List<ProgramSchemaValidationError> errors, String path, String message) {
    errors.add(new ProgramSchemaValidationError(path, message));
  }
}
