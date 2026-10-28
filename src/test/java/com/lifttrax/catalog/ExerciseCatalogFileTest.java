package com.lifttrax.catalog;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifttrax.models.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExerciseCatalogFileTest {
  private static String valid() throws Exception {
    return Files.readString(Path.of("shared/exercise-catalog/examples/exercise-catalog-v1.json"));
  }

  @Test
  void versionedSchemaLatestAndRepresentativeFileAgree() throws Exception {
    String numbered =
        Files.readString(Path.of("shared/exercise-catalog/schema/exercise-catalog.schema.v1.json"));
    assertEquals(
        numbered,
        Files.readString(
            Path.of("shared/exercise-catalog/schema/exercise-catalog.schema.latest.json")));
    var schema = new ObjectMapper().readTree(numbered);
    assertEquals(1, schema.path("properties").path("schemaVersion").path("const").asInt());
    var entries = ExerciseCatalogFile.parse(valid());
    assertEquals(3, entries.size());
    assertEquals("example:coach-catalog", entries.get(0).provenance().sourceNamespace());
    assertFalse(entries.get(2).enabled());
    assertFalse(entries.get(2).provenance().hasSource());
    assertEquals("file_import", entries.get(2).provenance().origin());
    String starter = valid().replace("example:coach-catalog", "lifttrax:starter");
    assertEquals("starter", ExerciseCatalogFile.parse(starter).get(0).provenance().origin());
  }

  @Test
  void errorsDistinguishSyntaxVersionSchemaAndDuplicateDefinitions() throws Exception {
    assertTrue(
        assertThrows(IllegalArgumentException.class, () -> ExerciseCatalogFile.parse("{"))
            .getMessage()
            .startsWith("Malformed JSON"));
    assertTrue(
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    ExerciseCatalogFile.parse(
                        valid().replace("\"schemaVersion\": 1", "\"schemaVersion\": 99")))
            .getMessage()
            .startsWith("Unsupported exercise-catalog"));
    var mapper = new ObjectMapper();
    for (String invalid :
        List.of(
            "null",
            "[]",
            "{}",
            "{\"schemaVersion\":\"1\"}",
            "{\"schemaVersion\":1,\"exercises\":[]}",
            "{\"schemaVersion\":1,\"exercises\":[null]}",
            "{\"schemaVersion\":1,\"exercises\":[{}]}",
            "{\"schemaVersion\":1,\"exercises\":[1]}",
            valid() + " {}")) {
      assertThrows(IllegalArgumentException.class, () -> ExerciseCatalogFile.parse(invalid));
    }
    var base = mapper.readTree(valid());
    for (String field : List.of("name", "enabled", "region", "main", "muscles", "notes")) {
      var root = base.deepCopy();
      ((com.fasterxml.jackson.databind.node.ObjectNode) root.path("exercises").get(0))
          .remove(field);
      assertTrue(
          assertThrows(
                  IllegalArgumentException.class, () -> ExerciseCatalogFile.parse(root.toString()))
              .getMessage()
              .contains("Schema validation"));
    }
    for (String replacement : List.of("SIDEWAYS", "upper", "")) {
      assertThrows(
          IllegalArgumentException.class,
          () -> ExerciseCatalogFile.parse(valid().replace("UPPER", replacement)));
    }
    for (String field :
        List.of("name", "enabled", "region", "main", "muscles", "notes", "source")) {
      var root = base.deepCopy();
      ((com.fasterxml.jackson.databind.node.ObjectNode) root.path("exercises").get(0))
          .put(field, 17);
      assertThrows(
          IllegalArgumentException.class, () -> ExerciseCatalogFile.parse(root.toString()));
    }
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ExerciseCatalogFile.parse(
                valid().replace("Paused Bench Press", " Paused Bench Press")));
    assertThrows(
        IllegalArgumentException.class,
        () -> ExerciseCatalogFile.parse(valid().replace("BENCH_PRESS", "INVALID")));
    assertThrows(
        IllegalArgumentException.class,
        () -> ExerciseCatalogFile.parse(valid().replace("CHEST", "INVALID")));
    assertThrows(
        IllegalArgumentException.class,
        () -> ExerciseCatalogFile.parse(valid().replace("\"CHEST\",", "\"CHEST\", \"CHEST\",")));
    var duplicate = base.deepCopy();
    ((com.fasterxml.jackson.databind.node.ArrayNode) duplicate.path("exercises"))
        .add(duplicate.path("exercises").get(0).deepCopy());
    assertTrue(
        assertThrows(
                IllegalArgumentException.class,
                () -> ExerciseCatalogFile.parse(duplicate.toString()))
            .getMessage()
            .startsWith("Duplicate exercise definitions"));
    assertThrows(
        IllegalArgumentException.class,
        () -> ExerciseCatalogFile.parse(valid().replace("farmer-carry", "paused-bench")));
    assertThrows(
        IllegalArgumentException.class,
        () -> ExerciseCatalogFile.parse(valid().replace("Farmer Carry", "paused bench press")));
    var unknown = base.deepCopy();
    ((com.fasterxml.jackson.databind.node.ObjectNode) unknown).put("unexpected", true);
    assertThrows(
        IllegalArgumentException.class, () -> ExerciseCatalogFile.parse(unknown.toString()));
  }

  @Test
  void previewActionsAreExplicitAndSourceKeysAreAccountLocal() throws Exception {
    var source = ExerciseCatalogFile.parse(valid());
    var original = source.get(0);
    var plan = CatalogImportPlan.preview(source, List.of());
    assertTrue(plan.rows().stream().allMatch(row -> row.action() == CatalogImportPlan.Action.NEW));
    plan = CatalogImportPlan.preview(source, source);
    assertEquals(
        List.of(
            CatalogImportPlan.Action.UNCHANGED,
            CatalogImportPlan.Action.UNCHANGED,
            CatalogImportPlan.Action.SKIPPED),
        plan.rows().stream().map(CatalogImportPlan.Row::action).toList());
    var manual = new CatalogEntry(original.lift(), original.enabled(), CatalogProvenance.unknown());
    assertTrue(CatalogImportPlan.preview(List.of(original), List.of(manual)).hasConflicts());
    var changed =
        new CatalogEntry(
            new Lift(original.lift().name(), LiftRegion.LOWER, null, List.of(), "changed"),
            true,
            CatalogProvenance.unknown());
    assertTrue(CatalogImportPlan.preview(List.of(changed), List.of(manual)).hasConflicts());
    var renamed =
        new CatalogEntry(
            new Lift(
                "Renamed",
                original.lift().region(),
                original.lift().main(),
                original.lift().muscles(),
                original.lift().notes()),
            true,
            original.provenance());
    assertEquals(
        CatalogImportPlan.Action.CHANGED,
        CatalogImportPlan.preview(List.of(original), List.of(renamed)).rows().get(0).action());
    assertEquals(
        CatalogImportPlan.Action.NEW,
        CatalogImportPlan.preview(List.of(original), List.of()).rows().get(0).action());
    assertNotEquals(
        CatalogImportPlan.preview(List.of(original), List.of()).fingerprint(),
        CatalogImportPlan.preview(List.of(original), List.of(original)).fingerprint());
    assertThrows(
        IllegalArgumentException.class,
        () -> CatalogImportPlan.preview(List.of(original, renamed), List.of()));
    String nullable =
        valid()
            .replace("\"BENCH_PRESS\"", "null")
            .replace("\"revision\": \"1\"", "\"revision\": \"2\"");
    assertNull(ExerciseCatalogFile.parse(nullable).get(0).lift().main());
  }
}
