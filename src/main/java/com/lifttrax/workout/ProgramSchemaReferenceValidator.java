package com.lifttrax.workout;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Validates program references between templates, pools, rules, and blocks. */
final class ProgramSchemaReferenceValidator {
  private ProgramSchemaReferenceValidator() {}

  static void validate(JsonNode root, List<ProgramSchemaValidationError> errors) {
    validateTemplateReferences(root, errors);
    validateObjectReferences(root, "poolRef", "exercisePools", errors);
    validateObjectReferences(root, "progressionRuleRef", "progressionRules", errors);
    validateObjectReferences(root, "substitutionRuleRef", "substitutionRules", errors);
    validateAllowedPoolReferences(root, errors);
    validateBlockReferences(root, errors);
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
}
