package com.lifttrax.cli;

import static com.lifttrax.cli.WebServerCliTestSupport.TestExchange;
import static com.lifttrax.cli.WebServerCliTestSupport.form;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lifttrax.catalog.CatalogImportPlan;
import com.lifttrax.catalog.ExerciseCatalogFile;
import com.lifttrax.db.HostedPostgresConfig;
import com.lifttrax.db.HostedPostgresTrainingDataStoreProvider;
import com.lifttrax.db.TrainingDataStore;
import com.lifttrax.db.TrainingDataStoreProvider;
import com.lifttrax.workout.ProgramSchemaValidator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExerciseCatalogRouteTest {
  @Test
  void catalogImportGetRendersTheFileChooser() throws Exception {
    try (CatalogFixture fixture = fixture()) {
      TestExchange exchange = TestExchange.get("/import-exercise-catalog");

      WebAccountRoutes.handleExerciseCatalogImport(exchange, fixture.provider());

      assertTrue(exchange.responseBody().contains("type='file'"));
      assertFalse(exchange.responseBody().contains("Confirm and save catalog"));
    }
  }

  @Test
  void catalogPreviewRequiresConfirmationWithoutWriting() throws Exception {
    try (CatalogFixture fixture = fixture()) {
      TestExchange exchange =
          TestExchange.post(
              "/import-exercise-catalog",
              form("action", "preview", "catalogJson", fixture.document()));

      WebAccountRoutes.handleExerciseCatalogImport(exchange, fixture.provider());

      assertTrue(exchange.responseBody().contains("Confirm and save catalog"));
      assertTrue(fixture.owner().listLifts().isEmpty());
    }
  }

  @Test
  void catalogConfirmationRejectsAStaleFingerprint() throws Exception {
    try (CatalogFixture fixture = fixture()) {
      TestExchange exchange =
          TestExchange.post(
              "/import-exercise-catalog",
              form("action", "confirm", "catalogJson", fixture.document(), "fingerprint", "wrong"));

      WebAccountRoutes.handleExerciseCatalogImport(exchange, fixture.provider());

      assertTrue(exchange.responseBody().contains("Catalog changed since preview"));
      assertTrue(fixture.owner().listLifts().isEmpty());
    }
  }

  @Test
  void catalogConfirmationWritesOnlyTheCurrentAccount() throws Exception {
    try (CatalogFixture fixture = fixture()) {
      TestExchange exchange = confirmation(fixture);

      WebAccountRoutes.handleExerciseCatalogImport(exchange, fixture.provider());

      assertTrue(exchange.responseBody().contains("Saved 3 catalog changes"));
      assertEquals(3, fixture.owner().listLifts().size());
      assertFalse(fixture.owner().isLiftEnabled("Retired Curl"));
      assertTrue(fixture.other().listLifts().isEmpty());
    }
  }

  @Test
  void catalogPreviewReportsUnchangedEntriesAfterImport() throws Exception {
    try (CatalogFixture fixture = fixture()) {
      WebAccountRoutes.handleExerciseCatalogImport(confirmation(fixture), fixture.provider());
      TestExchange exchange = preview(fixture.document());

      WebAccountRoutes.handleExerciseCatalogImport(exchange, fixture.provider());

      assertTrue(exchange.responseBody().contains("unchanged"));
      assertTrue(exchange.responseBody().contains("skipped"));
    }
  }

  @Test
  void catalogPreviewBlocksSourceConflicts() throws Exception {
    try (CatalogFixture fixture = fixture()) {
      WebAccountRoutes.handleExerciseCatalogImport(confirmation(fixture), fixture.provider());
      String conflictDocument = fixture.document().replace("paused-bench", "new-source");
      TestExchange exchange = preview(conflictDocument);

      WebAccountRoutes.handleExerciseCatalogImport(exchange, fixture.provider());

      assertTrue(exchange.responseBody().contains("conflict"));
      assertFalse(exchange.responseBody().contains("Confirm and save catalog"));
    }
  }

  @Test
  void catalogPreviewRejectsMalformedAndUnsupportedDocuments() throws Exception {
    try (CatalogFixture fixture = fixture()) {
      for (String invalid : List.of("{", "{\"schemaVersion\":99}", "{}")) {
        TestExchange exchange = preview(invalid);

        WebAccountRoutes.handleExerciseCatalogImport(exchange, fixture.provider());

        assertTrue(exchange.responseBody().contains("status error"), invalid);
      }
    }
  }

  @Test
  void catalogImportRejectsUnknownActions() throws Exception {
    try (CatalogFixture fixture = fixture()) {
      TestExchange exchange =
          TestExchange.post(
              "/import-exercise-catalog",
              form("action", "overwrite", "catalogJson", fixture.document()));

      WebAccountRoutes.handleExerciseCatalogImport(exchange, fixture.provider());

      assertTrue(exchange.responseBody().contains("Choose Preview catalog"));
    }
  }

  @Test
  void catalogImportSanitizesStorageFailures() throws Exception {
    TrainingDataStoreProvider failed =
        user -> {
          throw new java.sql.SQLException("private connection details");
        };
    TestExchange exchange = TestExchange.get("/import-exercise-catalog");

    WebAccountRoutes.handleExerciseCatalogImport(exchange, failed);

    assertTrue(exchange.responseBody().contains("Database write or read failed"));
    assertFalse(exchange.responseBody().contains("private connection details"));
  }

  @Test
  void workoutPreviewDoesNotImportUnknownExercisesIntoTheCatalog() throws Exception {
    try (CatalogFixture fixture = fixture()) {
      WebAccountRoutes.handleExerciseCatalogImport(confirmation(fixture), fixture.provider());
      String workout = Files.readString(Path.of("shared/workouts/examples/conjugate-wave-v2.json"));
      TestExchange exchange =
          TestExchange.post(
              "/planned-workout-preview",
              form("plannedWorkoutJson", workout.replace("Bench Press", "Unknown catalog bench")));

      WebWorkoutRoutes.handlePlannedWorkoutPreview(exchange, fixture.provider());

      assertEquals(200, exchange.status());
      assertEquals(3, fixture.owner().listLifts().size());
    }
  }

  @Test
  void programValidationDoesNotImportUnknownExercisesIntoTheCatalog() throws Exception {
    try (CatalogFixture fixture = fixture()) {
      WebAccountRoutes.handleExerciseCatalogImport(confirmation(fixture), fixture.provider());
      String program = Files.readString(Path.of("shared/programs/examples/conjugate-v1.json"));

      boolean valid =
          new ProgramSchemaValidator()
              .validate(program.replace("Back Squat", "Unknown catalog squat"))
              .isValid();

      assertTrue(valid);
      assertEquals(3, fixture.owner().listLifts().size());
    }
  }

  private static TestExchange preview(String document) {
    return TestExchange.post(
        "/import-exercise-catalog", form("action", "preview", "catalogJson", document));
  }

  private static TestExchange confirmation(CatalogFixture fixture) throws Exception {
    var entries = ExerciseCatalogFile.parse(fixture.document());
    String fingerprint =
        CatalogImportPlan.preview(entries, fixture.owner().listCatalogEntries()).fingerprint();
    return TestExchange.post(
        "/import-exercise-catalog",
        form("action", "confirm", "catalogJson", fixture.document(), "fingerprint", fingerprint));
  }

  private static CatalogFixture fixture() throws Exception {
    var config =
        new HostedPostgresConfig(
            "jdbc:h2:mem:catalog_route_"
                + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "",
            "");
    var provider = new HostedPostgresTrainingDataStoreProvider(config);
    return new CatalogFixture(
        provider,
        provider.forUser("local-user"),
        provider.forUser("other-user"),
        Files.readString(Path.of("shared/exercise-catalog/examples/exercise-catalog-v1.json")));
  }

  private record CatalogFixture(
      HostedPostgresTrainingDataStoreProvider provider,
      TrainingDataStore owner,
      TrainingDataStore other,
      String document)
      implements AutoCloseable {
    @Override
    public void close() throws Exception {
      provider.close();
    }
  }
}
