package com.lifttrax.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lifttrax.workout.PlannedWorkoutFile;
import com.lifttrax.workout.PlannedWorkoutJson;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlannedWorkoutPreviewNavigationHtmlTest {
  @Test
  void singleWeekKeepsDirectHeadingTargetWithoutNavigationControls() throws Exception {
    String html = PlannedWorkoutHtml.renderPage(workout(List.of(7)));

    assertTrue(
        html.contains(
            "tabindex='-1' class='preview-week-heading' id='preview-week-7'>Week 7</h2>"));
    assertFalse(html.contains("aria-label='Preview weeks'"));
    assertFalse(html.contains(">Previous Week</button>"));
    assertFalse(html.contains(">Next Week</button>"));
    assertTrue(html.contains("formaction='/planned-workout-work-along'"));
    assertTrue(html.contains("formaction='/planned-workout-print'"));
    assertTrue(html.contains("formaction='/planned-workout-markdown'"));
    assertTrue(html.contains("formaction='/planned-workout-json'"));
  }

  @Test
  void multipleWeeksUseActualNumbersAndFileOrderWithNoScriptLinks() throws Exception {
    String html = PlannedWorkoutHtml.renderPage(workout(List.of(5, 2, 9)));

    assertTrue(html.contains("aria-label='Preview weeks'"));
    assertTrue(
        html.contains(
            "<label for='preview-week-select'>Preview week<select id='preview-week-select'>"));
    assertTrue(
        html.contains(
            "<option value='preview-week-5'>Week 5</option><option value='preview-week-2'>Week 2</option><option value='preview-week-9'>Week 9</option>"));
    assertTrue(html.contains("type='button' class='secondary preview-week-previous' disabled"));
    assertTrue(html.contains("type='button' class='secondary preview-week-next'"));
    assertTrue(html.contains("class='preview-week-current' role='status'"));
    assertTrue(
        html.indexOf("aria-label='Preview weeks'")
            < html.indexOf("<section class='planned-week'>"));
    for (int number : List.of(5, 2, 9)) {
      assertTrue(html.contains("href='#preview-week-" + number + "'>Week " + number + "</a>"));
      assertTrue(html.contains("id='preview-week-" + number + "'>Week " + number + "</h2>"));
    }
    assertEquals(3, html.split("<section class='planned-week'>", -1).length - 1);
    assertFalse(html.contains("<option value='preview-week-1'>"));
  }

  @Test
  void previewNavigationDoesNotEnterOtherOutputs() throws Exception {
    var workout = workout(List.of(5, 2, 9));
    assertFalse(PlannedWorkoutHtml.renderWorkAlongPage(workout).contains("preview-week-"));
    assertFalse(PlannedWorkoutPrintHtml.renderPage(workout, null).contains("preview-week-"));
    assertFalse(
        String.join(
                "\n",
                com.lifttrax.workout.PlannedWorkoutMarkdownWriter.createMarkdown(workout, null))
            .contains("preview-week-"));
    assertFalse(PlannedWorkoutJson.writeString(workout).contains("preview-week-"));
  }

  private static PlannedWorkoutFile workout(List<Integer> numbers) throws Exception {
    var source =
        PlannedWorkoutJson.readPath(Path.of("shared/workouts/examples/conjugate-wave-v2.json"));
    return new PlannedWorkoutFile(
        source.schemaVersion(),
        new PlannedWorkoutFile.PlannedWorkoutMetadata(
            "Preview navigation", "Test", numbers.size(), List.of()),
        source.source(),
        numbers.stream()
            .map(
                number ->
                    new PlannedWorkoutFile.PlannedWorkoutWeek(number, source.weeks().get(0).days()))
            .toList(),
        source.completedWorkouts());
  }
}
