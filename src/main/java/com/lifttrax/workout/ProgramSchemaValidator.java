package com.lifttrax.workout;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Validates LiftTrax program schema files before wave generation starts. */
public final class ProgramSchemaValidator {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Map<Integer, ProgramSchemaRules> RULES_BY_VERSION =
      Map.of(
          1, ProgramSchemaValidator::validateCompatibleShape,
          2, ProgramSchemaValidator::validateCompatibleShape);

  public ProgramSchemaValidationResult validate(Path programFile) {
    try (Reader reader = Files.newBufferedReader(programFile, StandardCharsets.UTF_8)) {
      return validate(JSON.readTree(reader));
    } catch (JsonProcessingException exception) {
      return invalid("$", "Program file is not valid JSON: " + exception.getOriginalMessage());
    } catch (IOException exception) {
      return invalid("$", "Program file could not be read: " + exception.getMessage());
    }
  }

  public ProgramSchemaValidationResult validate(String programJson) {
    try {
      return validate(JSON.readTree(programJson));
    } catch (JsonProcessingException exception) {
      return invalid("$", "Program file is not valid JSON: " + exception.getOriginalMessage());
    }
  }

  public ProgramSchemaValidationResult validate(JsonNode root) {
    List<ProgramSchemaValidationError> errors = new ArrayList<>();
    if (root == null || !root.isObject()) {
      addError(errors, "$", "Program must be a JSON object.");
      return new ProgramSchemaValidationResult(errors);
    }

    Integer schemaVersion = validateSchemaVersion(root, errors);
    if (schemaVersion == null) {
      return new ProgramSchemaValidationResult(errors);
    }

    ProgramSchemaRules rules = RULES_BY_VERSION.get(schemaVersion);
    if (rules == null) {
      addError(
          errors,
          "$.schemaVersion",
          ProgramSchemaVersions.unsupportedVersionMessage(schemaVersion));
      return new ProgramSchemaValidationResult(errors);
    }
    rules.validate(root, errors);
    return new ProgramSchemaValidationResult(errors);
  }

  private static void validateCompatibleShape(
      JsonNode root, List<ProgramSchemaValidationError> errors) {
    validateTopLevelSections(root, errors);
    int durationWeeks =
        requiredPositiveInt(
            root.path("program").path("durationWeeks"), "$.program.durationWeeks", errors);
    int daysPerWeek =
        requiredPositiveInt(
            root.path("program").path("daysPerWeek"), "$.program.daysPerWeek", errors);

    validateWeeks(root, durationWeeks, daysPerWeek, errors);
    validateTemplateReferences(root, errors);
    validateObjectReferences(root, "poolRef", "exercisePools", errors);
    validateObjectReferences(root, "progressionRuleRef", "progressionRules", errors);
    validateObjectReferences(root, "substitutionRuleRef", "substitutionRules", errors);
    validateAllowedPoolReferences(root, errors);
    validateBlockReferences(root, errors);
    validateProgressions(root, durationWeeks, errors);
    validateRangePairs(root, "$", errors);
  }

  private static ProgramSchemaValidationResult invalid(String path, String message) {
    return new ProgramSchemaValidationResult(
        List.of(new ProgramSchemaValidationError(path, message)));
  }

  private static Integer validateSchemaVersion(
      JsonNode root, List<ProgramSchemaValidationError> errors) {
    JsonNode version = root.path("schemaVersion");
    if (version.isMissingNode()) {
      addError(errors, "$.schemaVersion", "Missing required schemaVersion.");
      return null;
    }
    if (!version.isIntegralNumber() || !version.canConvertToInt()) {
      addError(errors, "$.schemaVersion", "schemaVersion must be an integer.");
      return null;
    }
    int value = version.asInt();
    if (!ProgramSchemaVersions.supports(value)) {
      addError(errors, "$.schemaVersion", ProgramSchemaVersions.unsupportedVersionMessage(value));
      return null;
    }
    return value;
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

  private static int requiredPositiveInt(
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

  private static void validateTemplateReferences(
      JsonNode root, List<ProgramSchemaValidationError> errors) {
    Set<String> templateIds = objectFieldNames(root.path("dayTemplates"));
    List<FieldReference> references = new ArrayList<>();
    collectTextFields(root.path("weeks"), "$.weeks", "templateRef", references);
    for (FieldReference reference : references) {
      if (!templateIds.contains(reference.value())) {
        addError(errors, reference.path(), "Unknown templateRef \"" + reference.value() + "\".");
      }
    }
  }

  private static void validateObjectReferences(
      JsonNode root,
      String refField,
      String targetSection,
      List<ProgramSchemaValidationError> errors) {
    Set<String> targetIds = objectFieldNames(root.path(targetSection));
    List<FieldReference> references = new ArrayList<>();
    collectTextFields(root, "$", refField, references);
    for (FieldReference reference : references) {
      if (!targetIds.contains(reference.value())) {
        addError(
            errors, reference.path(), "Unknown " + refField + " \"" + reference.value() + "\".");
      }
    }
  }

  private static void validateAllowedPoolReferences(
      JsonNode root, List<ProgramSchemaValidationError> errors) {
    Set<String> poolIds = objectFieldNames(root.path("exercisePools"));
    JsonNode rules = root.path("substitutionRules");
    if (!rules.isObject()) {
      return;
    }

    Iterator<Map.Entry<String, JsonNode>> fields = rules.fields();
    while (fields.hasNext()) {
      Map.Entry<String, JsonNode> field = fields.next();
      String path = "$.substitutionRules." + field.getKey() + ".allowedPoolRefs";
      JsonNode allowedPoolRefs = field.getValue().path("allowedPoolRefs");
      if (!allowedPoolRefs.isArray()) {
        continue;
      }
      for (int index = 0; index < allowedPoolRefs.size(); index++) {
        String poolRef = allowedPoolRefs.get(index).asText();
        if (!poolIds.contains(poolRef)) {
          addError(
              errors,
              path + "[" + index + "]",
              "Unknown allowedPoolRefs value \"" + poolRef + "\".");
        }
      }
    }
  }

  private static void validateBlockReferences(
      JsonNode root, List<ProgramSchemaValidationError> errors) {
    JsonNode templates = root.path("dayTemplates");
    if (templates.isObject()) {
      Iterator<Map.Entry<String, JsonNode>> fields = templates.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> field = fields.next();
        validateBlockList(
            field.getValue().path("blocks"),
            "$.dayTemplates." + field.getKey() + ".blocks",
            errors);
      }
    }
    validateInlineDayBlocks(root.path("weeks"), "$.weeks", errors);
  }

  private static void validateInlineDayBlocks(
      JsonNode node, String path, List<ProgramSchemaValidationError> errors) {
    if (node.isObject()) {
      JsonNode blocks = node.path("blocks");
      if (blocks.isArray()) {
        validateBlockList(blocks, path + ".blocks", errors);
      }
      Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> field = fields.next();
        validateInlineDayBlocks(field.getValue(), path + "." + field.getKey(), errors);
      }
      return;
    }
    if (node.isArray()) {
      for (int index = 0; index < node.size(); index++) {
        validateInlineDayBlocks(node.get(index), path + "[" + index + "]", errors);
      }
    }
  }

  private static void validateBlockList(
      JsonNode blocks, String blocksPath, List<ProgramSchemaValidationError> errors) {
    if (!blocks.isArray()) {
      return;
    }
    Set<String> blockIds = new HashSet<>();
    for (int index = 0; index < blocks.size(); index++) {
      JsonNode id = blocks.get(index).path("id");
      if (id.isTextual() && !blockIds.add(id.asText())) {
        addError(
            errors,
            blocksPath + "[" + index + "].id",
            "Duplicate block id \"" + id.asText() + "\".");
      }
    }

    List<FieldReference> references = new ArrayList<>();
    collectTextFields(blocks, blocksPath, "blockRef", references);
    for (FieldReference reference : references) {
      if (!blockIds.contains(reference.value())) {
        addError(errors, reference.path(), "Unknown blockRef \"" + reference.value() + "\".");
      }
    }
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
    int week = requiredPositiveInt(value, path, errors);
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
    return requiredPositiveInt(value, path, errors);
  }

  private static Set<String> objectFieldNames(JsonNode node) {
    Set<String> names = new HashSet<>();
    if (!node.isObject()) {
      return names;
    }
    Iterator<String> fieldNames = node.fieldNames();
    while (fieldNames.hasNext()) {
      names.add(fieldNames.next());
    }
    return names;
  }

  private static void collectTextFields(
      JsonNode node, String path, String fieldName, List<FieldReference> references) {
    if (node.isObject()) {
      JsonNode value = node.path(fieldName);
      if (value.isTextual()) {
        references.add(new FieldReference(path + "." + fieldName, value.asText()));
      }

      Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> field = fields.next();
        collectTextFields(field.getValue(), path + "." + field.getKey(), fieldName, references);
      }
      return;
    }
    if (node.isArray()) {
      for (int index = 0; index < node.size(); index++) {
        collectTextFields(node.get(index), path + "[" + index + "]", fieldName, references);
      }
    }
  }

  private static void addError(
      List<ProgramSchemaValidationError> errors, String path, String message) {
    errors.add(new ProgramSchemaValidationError(path, message));
  }

  private record FieldReference(String path, String value) {}

  @FunctionalInterface
  private interface ProgramSchemaRules {
    void validate(JsonNode root, List<ProgramSchemaValidationError> errors);
  }
}
