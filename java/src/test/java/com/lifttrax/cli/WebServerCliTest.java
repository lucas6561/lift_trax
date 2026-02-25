package com.lifttrax.cli;

import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftRegion;
import com.lifttrax.models.LiftType;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.Muscle;
import com.lifttrax.models.SetMetric;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
                "<p>query result</p>",
                "<p>last week result</p>",
                "<p>wave result</p>",
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
}
