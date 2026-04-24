package com.lifttrax.cli;

import com.lifttrax.db.SqliteDb;
import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftRegion;
import com.lifttrax.models.LiftType;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.Muscle;
import com.lifttrax.models.SetMetric;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebServerCliTest {

    @Test
    void escapeHtmlEscapesSpecialCharacters() {
        String escaped = WebHtml.escapeHtml("<tag>'\"&");
        assertEquals("&lt;tag&gt;&#39;&quot;&amp;", escaped);
    }

    @SuppressWarnings("unchecked")
    @Test
    void parseQueryDecodesParams() throws Exception {
        Method method = WebServerCli.class.getDeclaredMethod("parseQuery", URI.class);
        method.setAccessible(true);

        Map<String, String> parsed = (Map<String, String>) method.invoke(null, URI.create("/lift?name=Back+Squat&q=front%20squat"));

        assertEquals("Back Squat", parsed.get("name"));
        assertEquals("front squat", parsed.get("q"));
    }

    @Test
    void formatSetsIncludesMetricWeightAndRpe() {
        String formatted = WebUiRenderer.formatSets(List.of(
                new ExecutionSet(new SetMetric.Reps(5), "225 lb", 8.5f),
                new ExecutionSet(new SetMetric.Reps(3), "245 lb", null)
        ));

        assertTrue(formatted.contains("5 reps @ 225 lb rpe 8.5"));
        assertTrue(formatted.contains("3 reps @ 245 lb"));
    }

    @Test
    void formatMainTypeHandlesNullMain() {
        Lift lift = new Lift("Mystery Lift", null, null, List.of(), "");
        String formatted = WebUiRenderer.formatMainType(lift);
        assertEquals("Unknown", formatted);
    }

    @Test
    void renderTabbedLayoutIncludesExpectedTabs() {
        List<Lift> lifts = List.of(
                new Lift("Back Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(Muscle.QUAD, Muscle.GLUTE), ""),
                new Lift("Bench Press", LiftRegion.UPPER, LiftType.BENCH_PRESS, List.of(Muscle.CHEST, Muscle.TRICEP), "")
        );
        String html = WebUiRenderer.renderTabbedLayout(
                lifts,
                "",
                "Back Squat",
                "query",
                "<p>execution result</p>",
                "<p>query result</p>",
                "<p>last week result</p>",
                "<form><input name='waveWeeks'/><button>Generate Wave</button></form><p>wave result</p>",
                "Saved",
                "success",
                WebUiRenderer.AddExecutionPrefill.empty(),
                LocalDate.parse("2026-01-01"),
                LocalDate.parse("2026-01-07"),
                6
        );

        assertTrue(html.contains("Add Execution"));
        assertTrue(html.contains("Executions"));
        assertTrue(html.contains("Workout Waves"));
        assertTrue(html.contains("Query"));
        assertTrue(html.contains("Last Week"));
        assertTrue(html.contains("data-initial-tab='query'"));
        assertTrue(html.contains("Run Query"));
        assertTrue(html.contains("data-filter-option"));
        assertTrue(html.contains("hasAttribute('data-filter-option')"));
        assertTrue(html.contains("select name='queryLift'"));
        assertTrue(html.contains("query result"));
        assertTrue(html.contains("js-filter-name"));
        assertTrue(html.split("js-filter-name", -1).length - 1 >= 4);
        assertTrue(html.contains("js-filter-muscle"));
        assertTrue(html.contains("multiple"));
        assertTrue(html.contains("js-clear-filters"));
        assertTrue(html.contains("name='waveWeeks'"));
        assertTrue(html.contains("Generate Wave"));
        assertTrue(html.contains("name='lastWeekStart' value='2026-01-01'"));
        assertTrue(html.contains("name='lastWeekEnd' value='2026-01-07'"));
        assertTrue(html.contains("← Previous Week"));
        assertTrue(html.contains("Current Week"));
        assertTrue(html.contains("last week result"));
        assertTrue(html.contains("wave result"));
        assertTrue(html.contains("data-muscles='QUAD,GLUTE'"));
        assertTrue(html.contains("<option value='QUAD'>QUAD</option>"));
        assertTrue(html.contains("Hold Ctrl/Cmd to select multiple"));
        assertTrue(html.contains("Back Squat"));
        assertTrue(html.contains("Save Execution"));
        assertTrue(html.contains("action='/add-execution'"));
        assertTrue(html.contains("Load Last"));
        assertTrue(html.contains("formaction='/load-last-execution'"));
        assertTrue(html.contains("New Lift"));
        assertTrue(html.contains("action='/add-lift'"));
        assertTrue(html.contains("execution result"));
        assertTrue(html.contains("name='metricType'"));
        assertTrue(html.contains("name='setCopies'"));
        assertTrue(html.contains("metricLabel(item)"));
        assertTrue(html.contains("status success"));
    }

    @Test
    void renderQueryContentRequiresSelection() {
        String html = WebUiRenderer.renderQueryContent(null, "");
        assertTrue(html.contains("Select a lift"));
    }

    @Test
    void addExecutionPrefillUsesSimpleWeightModeForPlainWeight() {
        List<Lift> lifts = List.of(
                new Lift("Back Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(Muscle.QUAD), "")
        );
        WebUiRenderer.AddExecutionPrefill prefill = new WebUiRenderer.AddExecutionPrefill(
                "Back Squat", "185 lb", "1", "", "reps", "5", "5", "5", "", false, false, ""
        );

        String html = WebUiRenderer.renderAddExecutionForm(lifts, "", "", prefill);

        assertTrue(html.contains("name='weightMode' value='weight' checked"));
        assertTrue(html.contains("name='weightMode' value='custom'"));
        assertTrue(html.contains("name='weightValue' value='185'"));
        assertTrue(html.contains("<option value='lb' selected>lb</option>"));
    }

    @Test
    void addExecutionPrefillUsesCustomModeForUnparsedWeight() {
        List<Lift> lifts = List.of(
                new Lift("Back Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(Muscle.QUAD), "")
        );
        WebUiRenderer.AddExecutionPrefill prefill = new WebUiRenderer.AddExecutionPrefill(
                "Back Squat", "weird weight text", "1", "", "reps", "5", "5", "5", "", false, false, ""
        );

        String html = WebUiRenderer.renderAddExecutionForm(lifts, "", "", prefill);

        assertTrue(html.contains("name='weightMode' value='custom' checked"));
        assertTrue(html.contains("name='customWeight' value='weird weight text'"));
    }

    @Test
    void addExecutionPrefillParsesBandAndAccomWeights() {
        List<Lift> lifts = List.of(
                new Lift("Back Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(Muscle.QUAD), "")
        );

        WebUiRenderer.AddExecutionPrefill bandsPrefill = new WebUiRenderer.AddExecutionPrefill(
                "Back Squat", "red+blue", "1", "", "reps", "5", "5", "5", "", false, false, ""
        );
        String bandsHtml = WebUiRenderer.renderAddExecutionForm(lifts, "", "", bandsPrefill);
        assertTrue(bandsHtml.contains("name='weightMode' value='bands' checked"));
        assertTrue(bandsHtml.contains("name='weightBandColors' value='red' checked"));
        assertTrue(bandsHtml.contains("name='weightBandColors' value='blue' checked"));

        WebUiRenderer.AddExecutionPrefill accomChainsPrefill = new WebUiRenderer.AddExecutionPrefill(
                "Back Squat", "225 lb+40c", "1", "", "reps", "5", "5", "5", "", false, false, ""
        );
        String accomChainsHtml = WebUiRenderer.renderAddExecutionForm(lifts, "", "", accomChainsPrefill);
        assertTrue(accomChainsHtml.contains("name='weightMode' value='accom' checked"));
        assertTrue(accomChainsHtml.contains("name='accomBar' value='225'"));
        assertTrue(accomChainsHtml.contains("name='accomChain' value='40'"));
        assertTrue(accomChainsHtml.contains("<option value='chains' selected>Chains</option>"));

        WebUiRenderer.AddExecutionPrefill accomBandsPrefill = new WebUiRenderer.AddExecutionPrefill(
                "Back Squat", "225 lb+red+blue", "1", "", "reps", "5", "5", "5", "", false, false, ""
        );
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
            conn.createStatement().execute("""
                    CREATE TABLE IF NOT EXISTS lifts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL UNIQUE,
                        region TEXT NOT NULL,
                        main_lift TEXT,
                        muscles TEXT NOT NULL,
                        notes TEXT NOT NULL DEFAULT ''
                    )
                    """);
            conn.createStatement().execute("""
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

            String html = WebUiRenderer.renderExecutionList(db, lifts, "", "Recorded lifts:");

            assertTrue(html.contains("action='/set-lift-enabled'"));
            assertTrue(html.contains("Enable for wave"));
            assertTrue(html.contains("Disabled"));
        }
    }



    @Test
    void indexBodyRendersWavePlannerEvenWhenAnotherTabIsActive() throws Exception {
        Path dbPath = Files.createTempFile("lifttrax-index", ".db");
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             SqliteDb db = new SqliteDb(dbPath.toString())) {
            conn.createStatement().execute("""
                    CREATE TABLE IF NOT EXISTS lifts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL UNIQUE,
                        region TEXT NOT NULL,
                        main_lift TEXT,
                        muscles TEXT NOT NULL,
                        notes TEXT NOT NULL DEFAULT ''
                    )
                    """);
            conn.createStatement().execute("""
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
            db.addLift("Conventional Deadlift", LiftRegion.LOWER, LiftType.DEADLIFT, List.of(Muscle.HAMSTRING), "");
            db.addLift("Bench Press", LiftRegion.UPPER, LiftType.BENCH_PRESS, List.of(Muscle.CHEST), "");
            db.addLift("Overhead Press", LiftRegion.UPPER, LiftType.OVERHEAD_PRESS, List.of(Muscle.SHOULDER), "");
            db.addLift("Sled Push", LiftRegion.LOWER, LiftType.CONDITIONING, List.of(Muscle.QUAD), "");
            db.addLift("Bike", LiftRegion.UPPER, LiftType.CONDITIONING, List.of(Muscle.CORE), "");
            db.addLift("Leg Swings", LiftRegion.LOWER, LiftType.MOBILITY, List.of(Muscle.QUAD), "");
            db.addLift("Shoulder CARs", LiftRegion.UPPER, LiftType.MOBILITY, List.of(Muscle.SHOULDER), "");
            db.addLift("Hamstring Curl", LiftRegion.LOWER, LiftType.ACCESSORY, List.of(Muscle.HAMSTRING), "");
            db.addLift("Leg Extension", LiftRegion.LOWER, LiftType.ACCESSORY, List.of(Muscle.QUAD), "");
            db.addLift("Plank", LiftRegion.LOWER, LiftType.ACCESSORY, List.of(Muscle.CORE), "");
            db.addLift("Lat Pulldown", LiftRegion.UPPER, LiftType.ACCESSORY, List.of(Muscle.LAT), "");
            db.addLift("Tricep Pushdown", LiftRegion.UPPER, LiftType.ACCESSORY, List.of(Muscle.TRICEP), "");
            db.addLift("Chest Fly", LiftRegion.UPPER, LiftType.ACCESSORY, List.of(Muscle.CHEST), "");

            String html = WebUiRenderer.renderIndexBody(
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
                    Map.of()
            );

            assertTrue(html.contains("name='waveType'"));
            assertTrue(html.contains("Generate Wave"));
        } finally {
            Files.deleteIfExists(dbPath);
        }
    }


    @Test
    void conjugateWavePlannerStillRendersBaseControlsWhenPoolsAreInsufficient() throws Exception {
        Path dbPath = Files.createTempFile("lifttrax-conjugate", ".db");
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             SqliteDb db = new SqliteDb(dbPath.toString())) {
            conn.createStatement().execute("""
                    CREATE TABLE IF NOT EXISTS lifts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL UNIQUE,
                        region TEXT NOT NULL,
                        main_lift TEXT,
                        muscles TEXT NOT NULL,
                        notes TEXT NOT NULL DEFAULT ''
                    )
                    """);
            conn.createStatement().execute("""
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
    void hypertrophyWavePlannerStillRendersGenerateButtonWhenConjugatePoolsAreInsufficient() throws Exception {
        Path dbPath = Files.createTempFile("lifttrax-wave", ".db");
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             SqliteDb db = new SqliteDb(dbPath.toString())) {
            conn.createStatement().execute("""
                    CREATE TABLE IF NOT EXISTS lifts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL UNIQUE,
                        region TEXT NOT NULL,
                        main_lift TEXT,
                        muscles TEXT NOT NULL,
                        notes TEXT NOT NULL DEFAULT ''
                    )
                    """);
            conn.createStatement().execute("""
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

            db.addLift("Bench Press", LiftRegion.UPPER, LiftType.BENCH_PRESS, List.of(Muscle.CHEST, Muscle.TRICEP), "");
            db.addLift("Overhead Press", LiftRegion.UPPER, LiftType.OVERHEAD_PRESS, List.of(Muscle.SHOULDER, Muscle.TRICEP), "");
            db.addLift("Leg Swings", LiftRegion.LOWER, LiftType.MOBILITY, List.of(Muscle.QUAD), "");
            db.addLift("Shoulder CARs", LiftRegion.UPPER, LiftType.MOBILITY, List.of(Muscle.SHOULDER), "");
            db.addLift("Hamstring Curl", LiftRegion.LOWER, LiftType.ACCESSORY, List.of(Muscle.HAMSTRING), "");
            db.addLift("Leg Extension", LiftRegion.LOWER, LiftType.ACCESSORY, List.of(Muscle.QUAD), "");
            db.addLift("Plank", LiftRegion.LOWER, LiftType.ACCESSORY, List.of(Muscle.CORE), "");
            db.addLift("Chest Fly", LiftRegion.UPPER, LiftType.ACCESSORY, List.of(Muscle.CHEST), "");
            db.addLift("Lat Pulldown", LiftRegion.UPPER, LiftType.ACCESSORY, List.of(Muscle.LAT), "");
            db.addLift("Tricep Pushdown", LiftRegion.UPPER, LiftType.ACCESSORY, List.of(Muscle.TRICEP), "");

            String html = WebUiRenderer.renderWaveContent(db, 2, Map.of("waveType", "hypertrophy"));

            assertTrue(html.contains("name='waveType'"));
            assertTrue(html.contains("Generate Wave"));
            assertFalse(html.contains("Failed to load wave planner"));
        } finally {
            Files.deleteIfExists(dbPath);
        }
    }

    @Test
    void selectExecutionForFlagsMatchesWarmupAndDeload() {
        LiftExecution normal = new LiftExecution(1, LocalDate.parse("2026-01-01"), List.of(new ExecutionSet(new SetMetric.Reps(5), "225 lb", null)), false, false, "");
        LiftExecution warmup = new LiftExecution(2, LocalDate.parse("2026-01-02"), List.of(new ExecutionSet(new SetMetric.Reps(3), "185 lb", null)), true, false, "");
        LiftExecution deload = new LiftExecution(3, LocalDate.parse("2026-01-03"), List.of(new ExecutionSet(new SetMetric.Reps(2), "135 lb", null)), false, true, "");
        LiftExecution both = new LiftExecution(4, LocalDate.parse("2026-01-04"), List.of(new ExecutionSet(new SetMetric.Reps(2), "95 lb", null)), true, true, "");

        List<LiftExecution> executions = List.of(both, deload, warmup, normal);

        assertEquals(normal, WebServerCli.selectExecutionForFlags(executions, false, false));
        assertEquals(warmup, WebServerCli.selectExecutionForFlags(executions, true, false));
        assertEquals(deload, WebServerCli.selectExecutionForFlags(executions, false, true));
        assertEquals(both, WebServerCli.selectExecutionForFlags(executions, true, true));
    }

    @Test
    void findExecutionByIdReturnsMatch() {
        LiftExecution first = new LiftExecution(10, LocalDate.parse("2026-01-01"), List.of(new ExecutionSet(new SetMetric.Reps(5), "225 lb", null)), false, false, "");
        LiftExecution second = new LiftExecution(11, LocalDate.parse("2026-01-02"), List.of(new ExecutionSet(new SetMetric.Reps(3), "185 lb", null)), false, false, "");

        LiftExecution found = WebServerCli.findExecutionById(List.of(first, second), 11);
        LiftExecution missing = WebServerCli.findExecutionById(List.of(first, second), 99);

        assertEquals(second, found);
        assertTrue(missing == null);
    }
}
