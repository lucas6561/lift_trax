package com.lifttrax.cli;

import static com.lifttrax.cli.WebServerCliTestSupport.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lifttrax.db.SqliteDb;
import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.LiftRegion;
import com.lifttrax.models.LiftType;
import com.lifttrax.models.Muscle;
import com.lifttrax.models.SetMetric;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WebServerCliRenderingTest {

  @Test
  void sharedPageStylesKeepAddExecutionFormCompactOnNarrowViewports() {
    String html = WebHtml.wrapPage("Test", "<p>body</p>");

    assertTrue(html.contains("@media (max-width: 720px)"));
    assertTrue(html.contains(".tabs { display: grid"));
    assertTrue(html.contains(".tab-filter-bar { display: grid"));
    assertTrue(html.contains(".add-execution-form .segmented"));
    assertTrue(html.contains("grid-template-columns: repeat(2, minmax(0, 1fr))"));
    assertTrue(html.contains(".quick-log-presets"));
    assertTrue(html.contains(".save-execution-btn"));
    assertTrue(html.contains("position: sticky"));
    assertTrue(html.contains(".session-set-grid"));
    assertTrue(html.contains(".save-workout-session-btn"));
  }

  @Test
  void sharedPageStylesKeepExecutionRowsReadableOnPhoneViewports() {
    String html = WebHtml.wrapPage("Test", "<p>body</p>");

    assertTrue(html.contains("@media (max-width: 720px)"));
    assertTrue(html.contains(".execution-list { display: grid"));
    assertTrue(html.contains(".js-exec-view { align-items: flex-start !important"));
    assertTrue(html.contains("white-space: normal !important"));
    assertTrue(html.contains("text-overflow: clip !important"));
    assertTrue(html.contains(".execution-edit-meta { display: grid !important"));
    assertTrue(html.contains(".js-set-row { display: grid !important"));
    assertTrue(html.contains(".js-set-weight,"));
    assertTrue(html.contains("grid-column: 1 / -1"));
  }

  @Test
  void renderQueryContentRequiresSelection() {
    String html = WebUiRenderer.renderQueryContent(null, "");
    assertTrue(html.contains("Select a lift"));
  }

  @Test
  void renderLastWeekContentIncludesEditControls() throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-last-week-edit", ".db");
    try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
      conn.createStatement()
          .execute(
              """
                    CREATE TABLE IF NOT EXISTS lifts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL UNIQUE,
                        region TEXT NOT NULL,
                        main_lift TEXT,
                        muscles TEXT NOT NULL,
                        notes TEXT NOT NULL DEFAULT ''
                    )
                    """);
      conn.createStatement()
          .execute(
              """
                    CREATE TABLE IF NOT EXISTS lift_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        lift_id INTEGER NOT NULL,
                        date TEXT NOT NULL,
                        sets TEXT NOT NULL,
                        warmup INTEGER NOT NULL DEFAULT 0,
                        deload INTEGER NOT NULL DEFAULT 0,
                        notes TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY(lift_id) REFERENCES lifts(id)
                    )
                    """);
    }

    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      db.addLift("Back Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(Muscle.QUAD), "");
      db.addLiftExecution(
          "Back Squat",
          new LiftExecution(
              null,
              LocalDate.parse("2026-01-05"),
              List.of(new ExecutionSet(new SetMetric.Reps(5), "225 lb", 8.5f)),
              false,
              false,
              "Felt good"));

      String html =
          WebUiRenderer.renderLastWeekContent(
              db, db.listLifts(), LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-07"));

      assertTrue(html.contains("js-exec-edit"));
      assertTrue(html.contains("js-exec-delete"));
      assertTrue(html.contains("type='button' class='secondary compact-btn js-exec-edit'"));
      assertTrue(
          html.contains("type='button' class='secondary danger compact-btn js-exec-delete'"));
      assertTrue(html.contains("name='tab' value='last-week'"));
      assertTrue(html.contains("name='lastWeekStart' value='2026-01-01'"));
      assertTrue(html.contains("name='lastWeekEnd' value='2026-01-07'"));
      assertTrue(html.contains("<h4>2026-01-05</h4>"));
      assertTrue(html.contains("Back Squat — 1 sets x 5 reps @ 225 lb RPE 8.5 - Felt good"));
    }
  }

  @Test
  void addExecutionPrefillUsesSimpleWeightModeForPlainWeight() {
    List<Lift> lifts =
        List.of(new Lift("Back Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(Muscle.QUAD), ""));
    WebUiRenderer.AddExecutionPrefill prefill =
        new WebUiRenderer.AddExecutionPrefill(
            "Back Squat", "185 lb", "1", "", "reps", "5", "5", "5", "", false, false, "");

    String html = WebUiRenderer.renderAddExecutionForm(lifts, "", "", prefill);

    assertTrue(html.contains("name='weightMode' value='weight' checked"));
    assertTrue(html.contains("name='weightMode' value='custom'"));
    assertTrue(html.contains("name='weightValue' data-focus-target='add-weight' value='185'"));
    assertTrue(html.contains("<option value='lb' selected>lb</option>"));
  }

  @Test
  void addExecutionPrefillUsesCustomModeForUnparsedWeight() {
    List<Lift> lifts =
        List.of(new Lift("Back Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(Muscle.QUAD), ""));
    WebUiRenderer.AddExecutionPrefill prefill =
        new WebUiRenderer.AddExecutionPrefill(
            "Back Squat",
            "weird weight text",
            "1",
            "",
            "reps",
            "5",
            "5",
            "5",
            "",
            false,
            false,
            "");

    String html = WebUiRenderer.renderAddExecutionForm(lifts, "", "", prefill);

    assertTrue(html.contains("name='weightMode' value='custom' checked"));
    assertTrue(
        html.contains(
            "name='customWeight' data-focus-target='add-weight' value='weird weight text'"));
  }

  @Test
  void addExecutionPrefillParsesBandAndAccomWeights() {
    List<Lift> lifts =
        List.of(new Lift("Back Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(Muscle.QUAD), ""));

    WebUiRenderer.AddExecutionPrefill bandsPrefill =
        new WebUiRenderer.AddExecutionPrefill(
            "Back Squat", "red+blue", "1", "", "reps", "5", "5", "5", "", false, false, "");
    String bandsHtml = WebUiRenderer.renderAddExecutionForm(lifts, "", "", bandsPrefill);
    assertTrue(bandsHtml.contains("name='weightMode' value='bands' checked"));
    assertTrue(bandsHtml.contains("name='weightBandColors' value='red' checked"));
    assertTrue(bandsHtml.contains("name='weightBandColors' value='blue' checked"));

    WebUiRenderer.AddExecutionPrefill accomChainsPrefill =
        new WebUiRenderer.AddExecutionPrefill(
            "Back Squat", "225 lb+40c", "1", "", "reps", "5", "5", "5", "", false, false, "");
    String accomChainsHtml =
        WebUiRenderer.renderAddExecutionForm(lifts, "", "", accomChainsPrefill);
    assertTrue(accomChainsHtml.contains("name='weightMode' value='accom' checked"));
    assertTrue(
        accomChainsHtml.contains("name='accomBar' data-focus-target='add-weight' value='225'"));
    assertTrue(accomChainsHtml.contains("name='accomChain' value='40'"));
    assertTrue(accomChainsHtml.contains("<option value='chains' selected>Chains</option>"));

    WebUiRenderer.AddExecutionPrefill accomBandsPrefill =
        new WebUiRenderer.AddExecutionPrefill(
            "Back Squat", "225 lb+red+blue", "1", "", "reps", "5", "5", "5", "", false, false, "");
    String accomBandsHtml = WebUiRenderer.renderAddExecutionForm(lifts, "", "", accomBandsPrefill);
    assertTrue(accomBandsHtml.contains("name='weightMode' value='accom' checked"));
    assertTrue(accomBandsHtml.contains("<option value='bands' selected>Bands</option>"));
    assertTrue(accomBandsHtml.contains("name='accomBandColors' value='red' checked"));
    assertTrue(accomBandsHtml.contains("name='accomBandColors' value='blue' checked"));
  }

  @Test
  void executionListShowsEnableDisableControl() throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-enabled-ui", ".db");
    try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
      conn.createStatement()
          .execute(
              """
                    CREATE TABLE IF NOT EXISTS lifts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL UNIQUE,
                        region TEXT NOT NULL,
                        main_lift TEXT,
                        muscles TEXT NOT NULL,
                        notes TEXT NOT NULL DEFAULT ''
                    )
                    """);
      conn.createStatement()
          .execute(
              """
                    CREATE TABLE IF NOT EXISTS lift_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        lift_id INTEGER NOT NULL,
                        date TEXT NOT NULL,
                        sets TEXT NOT NULL,
                        warmup INTEGER NOT NULL DEFAULT 0,
                        deload INTEGER NOT NULL DEFAULT 0,
                        notes TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY(lift_id) REFERENCES lifts(id)
                    )
                    """);
    }

    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      db.addLift("Back Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(), "");
      db.setLiftEnabled("Back Squat", false);
      List<Lift> lifts = db.listLifts();

      String html = ExecutionListHtml.renderCatalogList(db, lifts, "", "Exercise catalog:");

      assertTrue(html.contains("action='/set-lift-enabled'"));
      assertTrue(html.contains("Enable for wave"));
      assertTrue(html.contains("Disabled"));
    }
  }

  @Test
  void executionRowsShowWaveDisabledStatus() throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-enabled-rows", ".db");
    try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
      conn.createStatement()
          .execute(
              """
                    CREATE TABLE IF NOT EXISTS lifts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL UNIQUE,
                        region TEXT NOT NULL,
                        main_lift TEXT,
                        muscles TEXT NOT NULL,
                        notes TEXT NOT NULL DEFAULT ''
                    )
                    """);
      conn.createStatement()
          .execute(
              """
                    CREATE TABLE IF NOT EXISTS lift_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        lift_id INTEGER NOT NULL,
                        date TEXT NOT NULL,
                        sets TEXT NOT NULL,
                        warmup INTEGER NOT NULL DEFAULT 0,
                        deload INTEGER NOT NULL DEFAULT 0,
                        notes TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY(lift_id) REFERENCES lifts(id)
                    )
                    """);
    }
    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      db.addLift("Back Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(), "");
      db.addLiftExecution(
          "Back Squat",
          new LiftExecution(
              null,
              LocalDate.parse("2026-03-10"),
              List.of(new ExecutionSet(new SetMetric.Reps(5), "225 lb", null)),
              true,
              false,
              ""));
      db.setLiftEnabled("Back Squat", false);

      String html = WebUiRenderer.renderExecutionRows(db, "Back Squat");

      assertTrue(html.contains("Wave status: Disabled for wave"));
      assertTrue(html.contains("(warm-up)"));
    }
  }

  @Test
  void indexBodyDefersWavePlannerWhenAnotherTabIsActive() throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-index", ".db");
    try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        SqliteDb db = new SqliteDb(dbPath.toString())) {
      conn.createStatement()
          .execute(
              """
                    CREATE TABLE IF NOT EXISTS lifts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL UNIQUE,
                        region TEXT NOT NULL,
                        main_lift TEXT,
                        muscles TEXT NOT NULL,
                        notes TEXT NOT NULL DEFAULT ''
                    )
                    """);
      conn.createStatement()
          .execute(
              """
                    CREATE TABLE IF NOT EXISTS lift_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        lift_id INTEGER NOT NULL,
                        date TEXT NOT NULL,
                        sets TEXT NOT NULL,
                        warmup INTEGER NOT NULL DEFAULT 0,
                        deload INTEGER NOT NULL DEFAULT 0,
                        notes TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY(lift_id) REFERENCES lifts(id)
                    )
                    """);

      db.addLift("Back Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(Muscle.QUAD), "");
      db.addLift(
          "Conventional Deadlift",
          LiftRegion.LOWER,
          LiftType.DEADLIFT,
          List.of(Muscle.HAMSTRING),
          "");
      db.addLift("Bench Press", LiftRegion.UPPER, LiftType.BENCH_PRESS, List.of(Muscle.CHEST), "");
      db.addLift(
          "Overhead Press",
          LiftRegion.UPPER,
          LiftType.OVERHEAD_PRESS,
          List.of(Muscle.SHOULDER),
          "");
      db.addLift("Sled Push", LiftRegion.LOWER, LiftType.CONDITIONING, List.of(Muscle.QUAD), "");
      db.addLift("Bike", LiftRegion.UPPER, LiftType.CONDITIONING, List.of(Muscle.CORE), "");
      db.addLift("Leg Swings", LiftRegion.LOWER, LiftType.MOBILITY, List.of(Muscle.QUAD), "");
      db.addLift(
          "Shoulder CARs", LiftRegion.UPPER, LiftType.MOBILITY, List.of(Muscle.SHOULDER), "");
      db.addLift(
          "Hamstring Curl", LiftRegion.LOWER, LiftType.ACCESSORY, List.of(Muscle.HAMSTRING), "");
      db.addLift("Leg Extension", LiftRegion.LOWER, LiftType.ACCESSORY, List.of(Muscle.QUAD), "");
      db.addLift("Plank", LiftRegion.LOWER, LiftType.ACCESSORY, List.of(Muscle.CORE), "");
      db.addLift("Lat Pulldown", LiftRegion.UPPER, LiftType.ACCESSORY, List.of(Muscle.LAT), "");
      db.addLift(
          "Tricep Pushdown", LiftRegion.UPPER, LiftType.ACCESSORY, List.of(Muscle.TRICEP), "");
      db.addLift("Chest Fly", LiftRegion.UPPER, LiftType.ACCESSORY, List.of(Muscle.CHEST), "");

      String html =
          WebUiRenderer.renderIndexBody(
              db,
              List.of(),
              "",
              "",
              "add-execution",
              "",
              "",
              WebUiRenderer.AddExecutionPrefill.empty(),
              LocalDate.parse("2026-01-01"),
              LocalDate.parse("2026-01-07"),
              1,
              Map.of());

      assertFalse(html.contains("<select name='waveType'"));
      assertFalse(html.contains("Generate Wave"));
      assertTrue(html.contains("data-panel='waves' data-loaded='false'"));
      assertTrue(html.contains("Open this tab to load Workout Waves."));
    } finally {
      Files.deleteIfExists(dbPath);
    }
  }

  @Test
  void indexBodyRendersWavePlannerWhenWavesTabIsActive() throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-index-waves", ".db");
    try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        SqliteDb db = new SqliteDb(dbPath.toString())) {
      conn.createStatement()
          .execute(
              """
                    CREATE TABLE IF NOT EXISTS lifts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL UNIQUE,
                        region TEXT NOT NULL,
                        main_lift TEXT,
                        muscles TEXT NOT NULL,
                        notes TEXT NOT NULL DEFAULT ''
                    )
                    """);
      conn.createStatement()
          .execute(
              """
                    CREATE TABLE IF NOT EXISTS lift_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        lift_id INTEGER NOT NULL,
                        date TEXT NOT NULL,
                        sets TEXT NOT NULL,
                        warmup INTEGER NOT NULL DEFAULT 0,
                        deload INTEGER NOT NULL DEFAULT 0,
                        notes TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY(lift_id) REFERENCES lifts(id)
                    )
                    """);

      db.addLift("Back Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(Muscle.QUAD), "");
      db.addLift(
          "Conventional Deadlift",
          LiftRegion.LOWER,
          LiftType.DEADLIFT,
          List.of(Muscle.HAMSTRING),
          "");
      db.addLift("Bench Press", LiftRegion.UPPER, LiftType.BENCH_PRESS, List.of(Muscle.CHEST), "");
      db.addLift(
          "Overhead Press",
          LiftRegion.UPPER,
          LiftType.OVERHEAD_PRESS,
          List.of(Muscle.SHOULDER),
          "");

      String html =
          WebUiRenderer.renderIndexBody(
              db,
              List.of(),
              "",
              "",
              "waves",
              "",
              "",
              WebUiRenderer.AddExecutionPrefill.empty(),
              LocalDate.parse("2026-01-01"),
              LocalDate.parse("2026-01-07"),
              1,
              Map.of());

      assertTrue(html.contains("name='waveType'"));
      assertTrue(html.contains("Generate Wave"));
      assertTrue(html.contains("data-panel='waves' data-loaded='true'"));
    } finally {
      Files.deleteIfExists(dbPath);
    }
  }

  @Test
  void conjugateWavePlannerStillRendersBaseControlsWhenPoolsAreInsufficient() throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-conjugate", ".db");
    try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        SqliteDb db = new SqliteDb(dbPath.toString())) {
      conn.createStatement()
          .execute(
              """
                    CREATE TABLE IF NOT EXISTS lifts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL UNIQUE,
                        region TEXT NOT NULL,
                        main_lift TEXT,
                        muscles TEXT NOT NULL,
                        notes TEXT NOT NULL DEFAULT ''
                    )
                    """);
      conn.createStatement()
          .execute(
              """
                    CREATE TABLE IF NOT EXISTS lift_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        lift_id INTEGER NOT NULL,
                        date TEXT NOT NULL,
                        sets TEXT NOT NULL,
                        warmup INTEGER NOT NULL DEFAULT 0,
                        deload INTEGER NOT NULL DEFAULT 0,
                        notes TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY(lift_id) REFERENCES lifts(id)
                    )
                    """);

      db.addLift("Bench Press", LiftRegion.UPPER, LiftType.BENCH_PRESS, List.of(Muscle.CHEST), "");

      String html = WebUiRenderer.renderWaveContent(db, 7, Map.of("waveType", "conjugate"));

      assertTrue(html.contains("name='waveType'"));
      assertTrue(html.contains("Generate Wave"));
      assertTrue(html.contains("Failed to load conjugate planner options"));
    } finally {
      Files.deleteIfExists(dbPath);
    }
  }

  @Test
  void conjugateWavePlannerDefaultsDynamicArToAccommodatingResistance() throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-conjugate-ar", ".db");
    try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        SqliteDb db = new SqliteDb(dbPath.toString())) {
      conn.createStatement()
          .execute(
              """
                    CREATE TABLE IF NOT EXISTS lifts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL UNIQUE,
                        region TEXT NOT NULL,
                        main_lift TEXT,
                        muscles TEXT NOT NULL,
                        notes TEXT NOT NULL DEFAULT ''
                    )
                    """);
      conn.createStatement()
          .execute(
              """
                    CREATE TABLE IF NOT EXISTS lift_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        lift_id INTEGER NOT NULL,
                        date TEXT NOT NULL,
                        sets TEXT NOT NULL,
                        warmup INTEGER NOT NULL DEFAULT 0,
                        deload INTEGER NOT NULL DEFAULT 0,
                        notes TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY(lift_id) REFERENCES lifts(id)
                    )
                    """);

      db.addLift("Back Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(Muscle.QUAD), "");
      db.addLift(
          "Conventional Deadlift",
          LiftRegion.LOWER,
          LiftType.DEADLIFT,
          List.of(Muscle.HAMSTRING),
          "");
      db.addLift("Bench Press", LiftRegion.UPPER, LiftType.BENCH_PRESS, List.of(Muscle.CHEST), "");
      db.addLift(
          "Overhead Press",
          LiftRegion.UPPER,
          LiftType.OVERHEAD_PRESS,
          List.of(Muscle.SHOULDER),
          "");
      db.addLift("Sled Push", LiftRegion.LOWER, LiftType.CONDITIONING, List.of(Muscle.QUAD), "");
      db.addLift("Bike", LiftRegion.UPPER, LiftType.CONDITIONING, List.of(Muscle.CORE), "");
      db.addLift("Leg Swings", LiftRegion.LOWER, LiftType.MOBILITY, List.of(Muscle.QUAD), "");
      db.addLift(
          "Shoulder CARs", LiftRegion.UPPER, LiftType.MOBILITY, List.of(Muscle.SHOULDER), "");
      db.addLift(
          "Hamstring Curl", LiftRegion.LOWER, LiftType.ACCESSORY, List.of(Muscle.HAMSTRING), "");
      db.addLift("Leg Extension", LiftRegion.LOWER, LiftType.ACCESSORY, List.of(Muscle.QUAD), "");
      db.addLift("Plank", LiftRegion.LOWER, LiftType.ACCESSORY, List.of(Muscle.CORE), "");
      db.addLift("Lat Pulldown", LiftRegion.UPPER, LiftType.ACCESSORY, List.of(Muscle.LAT), "");
      db.addLift(
          "Tricep Pushdown", LiftRegion.UPPER, LiftType.ACCESSORY, List.of(Muscle.TRICEP), "");
      db.addLift("Curl", LiftRegion.UPPER, LiftType.ACCESSORY, List.of(Muscle.BICEP), "");

      String html = WebUiRenderer.renderWaveContent(db, 1, Map.of("waveType", "conjugate"));

      assertFalse(html.contains("<option value='STRAIGHT' selected>"));
      assertTrue(
          html.contains("<option value='CHAINS' selected>")
              || html.contains("<option value='BANDS' selected>"));
    } finally {
      Files.deleteIfExists(dbPath);
    }
  }

  @Test
  void hypertrophyWavePlannerStillRendersGenerateButtonWhenConjugatePoolsAreInsufficient()
      throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-wave", ".db");
    try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        SqliteDb db = new SqliteDb(dbPath.toString())) {
      conn.createStatement()
          .execute(
              """
                    CREATE TABLE IF NOT EXISTS lifts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL UNIQUE,
                        region TEXT NOT NULL,
                        main_lift TEXT,
                        muscles TEXT NOT NULL,
                        notes TEXT NOT NULL DEFAULT ''
                    )
                    """);
      conn.createStatement()
          .execute(
              """
                    CREATE TABLE IF NOT EXISTS lift_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        lift_id INTEGER NOT NULL,
                        date TEXT NOT NULL,
                        sets TEXT NOT NULL,
                        warmup INTEGER NOT NULL DEFAULT 0,
                        deload INTEGER NOT NULL DEFAULT 0,
                        notes TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY(lift_id) REFERENCES lifts(id)
                    )
                    """);

      db.addLift(
          "Bench Press",
          LiftRegion.UPPER,
          LiftType.BENCH_PRESS,
          List.of(Muscle.CHEST, Muscle.TRICEP),
          "");
      db.addLift(
          "Overhead Press",
          LiftRegion.UPPER,
          LiftType.OVERHEAD_PRESS,
          List.of(Muscle.SHOULDER, Muscle.TRICEP),
          "");
      db.addLift("Leg Swings", LiftRegion.LOWER, LiftType.MOBILITY, List.of(Muscle.QUAD), "");
      db.addLift(
          "Shoulder CARs", LiftRegion.UPPER, LiftType.MOBILITY, List.of(Muscle.SHOULDER), "");
      db.addLift(
          "Hamstring Curl", LiftRegion.LOWER, LiftType.ACCESSORY, List.of(Muscle.HAMSTRING), "");
      db.addLift("Leg Extension", LiftRegion.LOWER, LiftType.ACCESSORY, List.of(Muscle.QUAD), "");
      db.addLift("Plank", LiftRegion.LOWER, LiftType.ACCESSORY, List.of(Muscle.CORE), "");
      db.addLift("Chest Fly", LiftRegion.UPPER, LiftType.ACCESSORY, List.of(Muscle.CHEST), "");
      db.addLift("Lat Pulldown", LiftRegion.UPPER, LiftType.ACCESSORY, List.of(Muscle.LAT), "");
      db.addLift(
          "Tricep Pushdown", LiftRegion.UPPER, LiftType.ACCESSORY, List.of(Muscle.TRICEP), "");

      String html = WebUiRenderer.renderWaveContent(db, 2, Map.of("waveType", "hypertrophy"));

      assertTrue(html.contains("name='waveType'"));
      assertTrue(html.contains("Generate Wave"));
      assertFalse(html.contains("Failed to load wave planner"));
    } finally {
      Files.deleteIfExists(dbPath);
    }
  }

  @Test
  void generatedWaveIncludesSaveAsMarkdownControl() throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-wave-save", ".db");
    try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        SqliteDb db = new SqliteDb(dbPath.toString())) {
      conn.createStatement()
          .execute(
              """
                    CREATE TABLE IF NOT EXISTS lifts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL UNIQUE,
                        region TEXT NOT NULL,
                        main_lift TEXT,
                        muscles TEXT NOT NULL,
                        notes TEXT NOT NULL DEFAULT ''
                    )
                    """);
      conn.createStatement()
          .execute(
              """
                    CREATE TABLE IF NOT EXISTS lift_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        lift_id INTEGER NOT NULL,
                        date TEXT NOT NULL,
                        sets TEXT NOT NULL,
                        warmup INTEGER NOT NULL DEFAULT 0,
                        deload INTEGER NOT NULL DEFAULT 0,
                        notes TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY(lift_id) REFERENCES lifts(id)
                    )
                    """);

      db.addLift("Back Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(Muscle.QUAD), "");
      db.addLift(
          "Conventional Deadlift",
          LiftRegion.LOWER,
          LiftType.DEADLIFT,
          List.of(Muscle.HAMSTRING),
          "");
      db.addLift("Bench Press", LiftRegion.UPPER, LiftType.BENCH_PRESS, List.of(Muscle.CHEST), "");
      db.addLift(
          "Overhead Press",
          LiftRegion.UPPER,
          LiftType.OVERHEAD_PRESS,
          List.of(Muscle.SHOULDER),
          "");
      db.addLift("Sled Push", LiftRegion.LOWER, LiftType.CONDITIONING, List.of(Muscle.QUAD), "");
      db.addLift("Bike", LiftRegion.UPPER, LiftType.CONDITIONING, List.of(Muscle.CORE), "");
      db.addLift("Leg Swings", LiftRegion.LOWER, LiftType.MOBILITY, List.of(Muscle.QUAD), "");
      db.addLift(
          "Shoulder CARs", LiftRegion.UPPER, LiftType.MOBILITY, List.of(Muscle.SHOULDER), "");
      db.addLift(
          "Hamstring Curl", LiftRegion.LOWER, LiftType.ACCESSORY, List.of(Muscle.HAMSTRING), "");
      db.addLift("Leg Extension", LiftRegion.LOWER, LiftType.ACCESSORY, List.of(Muscle.QUAD), "");
      db.addLift("Plank", LiftRegion.LOWER, LiftType.ACCESSORY, List.of(Muscle.CORE), "");
      db.addLift("Lat Pulldown", LiftRegion.UPPER, LiftType.ACCESSORY, List.of(Muscle.LAT), "");
      db.addLift(
          "Tricep Pushdown", LiftRegion.UPPER, LiftType.ACCESSORY, List.of(Muscle.TRICEP), "");
      db.addLift("Chest Fly", LiftRegion.UPPER, LiftType.ACCESSORY, List.of(Muscle.CHEST), "");

      String html =
          WebUiRenderer.renderWaveContent(
              db, 2, Map.of("waveType", "hypertrophy", "waveGenerate", "true"));

      assertTrue(html.contains("Save As Markdown"));
      assertTrue(html.contains("Save As Workout JSON"));
      assertTrue(html.contains("Work Along"));
      assertTrue(html.contains("App Preview"));
      assertTrue(html.contains("Print View"));
      assertTrue(html.contains("formaction='/planned-workout-work-along'"));
      assertTrue(html.contains("formaction='/planned-workout-preview'"));
      assertTrue(html.contains("formaction='/planned-workout-print'"));
      assertTrue(html.contains("formaction='/planned-workout-markdown'"));
      assertTrue(html.contains("formaction='/planned-workout-json'"));
      assertFalse(html.contains("Select Workout JSON"));
    } finally {
      Files.deleteIfExists(dbPath);
    }
  }

  @Test
  void importWorkoutTabUsesFilePickerDialog() throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-import-workout-tab", ".db");
    try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        SqliteDb db = new SqliteDb(dbPath.toString())) {
      conn.createStatement()
          .execute(
              """
                    CREATE TABLE IF NOT EXISTS lifts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL UNIQUE,
                        region TEXT NOT NULL,
                        main_lift TEXT,
                        muscles TEXT NOT NULL,
                        notes TEXT NOT NULL DEFAULT ''
                    )
                    """);
      conn.createStatement()
          .execute(
              """
                    CREATE TABLE IF NOT EXISTS lift_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        lift_id INTEGER NOT NULL,
                        date TEXT NOT NULL,
                        sets TEXT NOT NULL,
                        warmup INTEGER NOT NULL DEFAULT 0,
                        deload INTEGER NOT NULL DEFAULT 0,
                        notes TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY(lift_id) REFERENCES lifts(id)
                    )
                    """);

      String html =
          WebUiRenderer.renderIndexBody(
              db,
              List.of(),
              "",
              "",
              "import-workout",
              "",
              "",
              WebUiRenderer.AddExecutionPrefill.empty(),
              LocalDate.parse("2026-01-01"),
              LocalDate.parse("2026-01-07"),
              1,
              Map.of());

      assertTrue(html.contains("data-initial-tab='import-workout'"));
      assertTrue(html.contains("data-panel='import-workout' data-loaded='true'"));
      assertTrue(html.contains("type='file' accept='application/json,.json'"));
      assertTrue(html.contains("Select Workout JSON"));
      assertTrue(html.contains("class='js-planned-workout-json'"));
      assertTrue(html.contains("FileReader"));
      assertTrue(html.contains("App Preview"));
      assertTrue(html.contains("Print View"));
      assertTrue(html.contains("Save As Markdown"));
      assertTrue(html.contains("Save As Workout JSON"));
      assertTrue(html.contains("Work Along"));
      assertTrue(html.contains("formaction='/planned-workout-work-along'"));
      assertTrue(html.contains("formaction='/planned-workout-preview'"));
      assertTrue(html.contains("formaction='/planned-workout-print'"));
      assertTrue(html.contains("formaction='/planned-workout-markdown'"));
      assertTrue(html.contains("formaction='/planned-workout-json'"));
      assertTrue(html.contains("outputButtons.forEach((button) => button.disabled = false)"));
      assertFalse(html.contains("Workout File Path"));
      assertFalse(html.contains("Paste File Contents"));
    } finally {
      Files.deleteIfExists(dbPath);
    }
  }
}
