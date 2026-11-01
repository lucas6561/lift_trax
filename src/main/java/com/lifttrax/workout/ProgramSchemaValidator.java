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
import java.util.List;
import java.util.Map;

/** Validates LiftTrax program schema files before wave generation starts. */
public final class ProgramSchemaValidator {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Map<Integer, ProgramSchemaRules> RULES_BY_VERSION =
      Map.of(
          1, ProgramSchemaValidator::validateCompatibleShape,
          2, ProgramSchemaValidator::validateCompatibleShape,
          3, ProgramSchemaValidator::validateCompatibleShape);

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
    ProgramSchemaStructureValidator.Dimensions dimensions =
        ProgramSchemaStructureValidator.validate(root, errors);
    ProgramSchemaReferenceValidator.validate(root, errors);
    ProgramSchemaRuleValidator.validate(root, dimensions.durationWeeks(), errors);
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

  private static void addError(
      List<ProgramSchemaValidationError> errors, String path, String message) {
    errors.add(new ProgramSchemaValidationError(path, message));
  }

  @FunctionalInterface
  private interface ProgramSchemaRules {
    void validate(JsonNode root, List<ProgramSchemaValidationError> errors);
  }
}
