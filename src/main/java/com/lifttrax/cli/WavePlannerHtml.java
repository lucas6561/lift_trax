package com.lifttrax.cli;

import com.lifttrax.db.TrainingDataStore;
import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftType;
import com.lifttrax.workout.ConjugateWorkoutBuilder;
import com.lifttrax.workout.DeloadWorkoutBuilder;
import com.lifttrax.workout.DynamicLifts;
import com.lifttrax.workout.HypertrophyWorkoutBuilder;
import com.lifttrax.workout.MaxEffortLiftPools;
import com.lifttrax.workout.MaxEffortPlan;
import com.lifttrax.workout.PlannedWorkoutExporter;
import com.lifttrax.workout.PlannedWorkoutFile;
import com.lifttrax.workout.PlannedWorkoutMarkdownWriter;
import com.lifttrax.workout.WebConfiguredDynamicLiftSource;
import com.lifttrax.workout.WebConfiguredMaxEffortPlanSource;
import com.lifttrax.workout.WorkoutBuilder;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Renders workout-wave configuration and generated output. */
final class WavePlannerHtml {
  private WavePlannerHtml() {}

  static String renderWaveContent(TrainingDataStore db, int weeks, Map<String, String> waveInput) {
    int normalizedWeeks = Math.max(1, weeks);
    String planner = renderWavePlannerForm(db, normalizedWeeks, waveInput);
    boolean shouldGenerate = "true".equalsIgnoreCase(waveInput.getOrDefault("waveGenerate", ""));
    try {
      if (!shouldGenerate) {
        return planner + "<p>Review movements, then click Generate Wave.</p>";
      }

      String waveType =
          waveInput.getOrDefault("waveType", "conjugate").trim().toLowerCase(Locale.ROOT);
      WorkoutBuilder builder =
          switch (waveType) {
            case "hypertrophy" -> new HypertrophyWorkoutBuilder();
            case "deload" -> new DeloadWorkoutBuilder();
            default ->
                new ConjugateWorkoutBuilder(
                    new WebConfiguredMaxEffortPlanSource(normalizedWeeks, waveInput),
                    new WebConfiguredDynamicLiftSource(waveInput));
          };
      var wave = builder.getWave(normalizedWeeks, db);
      PlannedWorkoutFile plannedWorkout =
          PlannedWorkoutExporter.fromWave(waveProgramName(waveType), waveType, wave);
      List<String> markdown = PlannedWorkoutMarkdownWriter.createMarkdown(plannedWorkout, db);
      String markdownText = String.join("\n", markdown);
      return planner
          + """
                    <p>Configured weeks: <strong>%s</strong></p>
                    %s
                    <pre class='query-output'>%s</pre>
                    """
              .formatted(
                  normalizedWeeks,
                  PlannedWorkoutHtml.renderOutputActions(plannedWorkout),
                  WebHtml.escapeHtml(markdownText));
    } catch (Exception e) {
      return planner
          + """
                    <p>Configured weeks: <strong>%s</strong></p>
                    <p class='status error'>Failed to generate wave: %s</p>
                    """
              .formatted(normalizedWeeks, WebHtml.escapeHtml(e.getMessage()));
    }
  }

  private static String waveProgramName(String waveType) {
    return switch (waveType) {
      case "hypertrophy" -> "Hypertrophy Wave";
      case "deload" -> "Deload Wave";
      default -> "Conjugate Wave";
    };
  }

  private static String renderWavePlannerForm(
      TrainingDataStore db, int weeks, Map<String, String> values) {
    StringBuilder html = new StringBuilder();
    appendPlannerHeader(html, weeks, values);
    try {
      appendConjugatePlanner(html, PlannerOptions.load(db, weeks), weeks, values);
    } catch (Exception e) {
      html.append("<p class='status error'>Failed to load conjugate planner options: ")
          .append(WebHtml.escapeHtml(e.getMessage()))
          .append("</p>");
    }
    return html.append(
            "<div class='stacked-row'><button type='submit'>Generate Wave</button></div></form>")
        .toString();
  }

  private static void appendPlannerHeader(
      StringBuilder html, int weeks, Map<String, String> values) {
    String waveType = values.getOrDefault("waveType", "conjugate").trim().toLowerCase(Locale.ROOT);
    html.append("<form method='get' action='/' class='query-form' style='display:block;'>")
        .append("<input type='hidden' name='tab' value='waves'/>")
        .append("<input type='hidden' name='waveGenerate' value='true'/>")
        .append("<label>Weeks <input type='number' name='waveWeeks' min='1' max='24' value='")
        .append(weeks)
        .append("'/></label><label>Wave Type <select name='waveType'>")
        .append("<option value='conjugate'")
        .append(selected("conjugate", waveType))
        .append(">Conjugate</option><option value='hypertrophy'")
        .append(selected("hypertrophy", waveType))
        .append(">Hypertrophy</option><option value='deload'")
        .append(selected("deload", waveType))
        .append(">Deload</option></select></label>");
  }

  private static String selected(String expected, String actual) {
    return expected.equals(actual) ? " selected" : "";
  }

  private static void appendConjugatePlanner(
      StringBuilder html, PlannerOptions options, int weeks, Map<String, String> values) {
    html.append("<div class='js-conjugate-only'><h3>Max Effort Rotation</h3>");
    appendMaxEffortRotation(html, options, weeks, values);
    appendDeloadWeeks(html, options, values);
    appendDynamicEffort(html, options, values);
    html.append("</div>");
  }

  private static void appendMaxEffortRotation(
      StringBuilder html, PlannerOptions options, int weeks, Map<String, String> values) {
    for (int index = 0; index < weeks; index++) {
      int week = index + 1;
      if (week % 7 == 0) {
        continue;
      }
      List<Lift> lower = index % 2 == 0 ? options.squats() : options.deadlifts();
      List<Lift> upper = index % 2 == 0 ? options.benches() : options.overheads();
      String lowerKey = "meLowerWeek" + week;
      String upperKey = "meUpperWeek" + week;
      html.append("<div class='stacked-row'><label>Week ")
          .append(week)
          .append(" Lower <select name='")
          .append(lowerKey)
          .append("'>")
          .append(
              renderLiftOptions(
                  lower, values.getOrDefault(lowerKey, options.lowerDefaults().get(index).name())))
          .append("</select></label><label>Week ")
          .append(week)
          .append(" Upper <select name='")
          .append(upperKey)
          .append("'>")
          .append(
              renderLiftOptions(
                  upper, values.getOrDefault(upperKey, options.upperDefaults().get(index).name())))
          .append("</select></label></div>");
    }
  }

  private static void appendDeloadWeeks(
      StringBuilder html, PlannerOptions options, Map<String, String> values) {
    if (!options.lowerDeloads().isEmpty() || !options.upperDeloads().isEmpty()) {
      html.append("<h3>Deload Weeks</h3>");
    }
    for (int index = 0; index < options.lowerDeloads().size(); index++) {
      appendLowerDeload(html, options, values, index);
    }
    for (int index = 0; index < options.upperDeloads().size(); index++) {
      appendUpperDeload(html, options, values, index);
    }
  }

  private static void appendLowerDeload(
      StringBuilder html, PlannerOptions options, Map<String, String> values, int index) {
    int number = index + 1;
    MaxEffortPlan.DeloadLowerLifts defaults = options.lowerDeloads().get(index);
    String squatKey = "meLowerDeload" + number + "Squat";
    String deadliftKey = "meLowerDeload" + number + "Deadlift";
    html.append("<div class='stacked-row'><label>Week ")
        .append(number * 7)
        .append(" Lower Squat <select name='")
        .append(squatKey)
        .append("'>")
        .append(
            renderLiftOptions(
                options.squats(), values.getOrDefault(squatKey, defaults.squat().name())))
        .append("</select></label><label>Week ")
        .append(number * 7)
        .append(" Lower Deadlift <select name='")
        .append(deadliftKey)
        .append("'>")
        .append(
            renderLiftOptions(
                options.deadlifts(), values.getOrDefault(deadliftKey, defaults.deadlift().name())))
        .append("</select></label></div>");
  }

  private static void appendUpperDeload(
      StringBuilder html, PlannerOptions options, Map<String, String> values, int index) {
    int number = index + 1;
    MaxEffortPlan.DeloadUpperLifts defaults = options.upperDeloads().get(index);
    String benchKey = "meUpperDeload" + number + "Bench";
    String overheadKey = "meUpperDeload" + number + "Overhead";
    html.append("<div class='stacked-row'><label>Week ")
        .append(number * 7)
        .append(" Upper Bench <select name='")
        .append(benchKey)
        .append("'>")
        .append(
            renderLiftOptions(
                options.benches(), values.getOrDefault(benchKey, defaults.bench().name())))
        .append("</select></label><label>Week ")
        .append(number * 7)
        .append(" Upper Overhead <select name='")
        .append(overheadKey)
        .append("'>")
        .append(
            renderLiftOptions(
                options.overheads(), values.getOrDefault(overheadKey, defaults.overhead().name())))
        .append("</select></label></div>");
  }

  private static void appendDynamicEffort(
      StringBuilder html, PlannerOptions options, Map<String, String> values) {
    html.append("<h3>Dynamic Effort Lifts</h3><div class='stacked-row'>")
        .append(dynamicLiftSelect("Squat", "deSquat", options.squats(), values))
        .append(dynamicLiftSelect("Deadlift", "deDeadlift", options.deadlifts(), values))
        .append(dynamicLiftSelect("Bench", "deBench", options.benches(), values))
        .append(dynamicLiftSelect("Overhead", "deOverhead", options.overheads(), values))
        .append("</div><div class='stacked-row'>")
        .append(
            dynamicArSelect(
                "Squat", "deSquatAr", options.dynamicDefaults().squat().ar().name(), values))
        .append(
            dynamicArSelect(
                "Deadlift",
                "deDeadliftAr",
                options.dynamicDefaults().deadlift().ar().name(),
                values))
        .append(
            dynamicArSelect(
                "Bench", "deBenchAr", options.dynamicDefaults().bench().ar().name(), values))
        .append(
            dynamicArSelect(
                "Overhead",
                "deOverheadAr",
                options.dynamicDefaults().overhead().ar().name(),
                values))
        .append("</div>");
  }

  private static String dynamicLiftSelect(
      String label, String name, List<Lift> lifts, Map<String, String> values) {
    return "<label>"
        + label
        + " <select name='"
        + name
        + "'>"
        + renderLiftOptions(lifts, values.get(name))
        + "</select></label>";
  }

  private static String dynamicArSelect(
      String label, String name, String defaultValue, Map<String, String> values) {
    return "<label>"
        + label
        + " AR "
        + renderArSelect(name, values.getOrDefault(name, defaultValue))
        + "</label>";
  }

  private record PlannerOptions(
      List<Lift> squats,
      List<Lift> deadlifts,
      List<Lift> benches,
      List<Lift> overheads,
      DynamicLifts dynamicDefaults,
      List<Lift> lowerDefaults,
      List<Lift> upperDefaults,
      List<MaxEffortPlan.DeloadLowerLifts> lowerDeloads,
      List<MaxEffortPlan.DeloadUpperLifts> upperDeloads) {
    static PlannerOptions load(TrainingDataStore db, int weeks) throws Exception {
      List<Lift> lower = new MaxEffortLiftPools(weeks, db).lowerWeeks();
      List<Lift> upper = new MaxEffortLiftPools(weeks, db).upperWeeks();
      return new PlannerOptions(
          db.liftsByType(LiftType.SQUAT),
          db.liftsByType(LiftType.DEADLIFT),
          db.liftsByType(LiftType.BENCH_PRESS),
          db.liftsByType(LiftType.OVERHEAD_PRESS),
          DynamicLifts.fromDatabase(db, false),
          lower,
          upper,
          MaxEffortPlan.deriveLowerDeloadFromPlan(lower),
          MaxEffortPlan.deriveUpperDeloadFromPlan(upper));
    }
  }

  static String renderLiftOptions(List<Lift> options, String selectedName) {
    StringBuilder html = new StringBuilder();
    for (Lift lift : options) {
      boolean selected = selectedName != null && selectedName.equals(lift.name());
      html.append("<option value='")
          .append(WebHtml.escapeHtml(lift.name()))
          .append("'")
          .append(selected ? " selected" : "")
          .append(">")
          .append(WebHtml.escapeHtml(lift.name()))
          .append("</option>");
    }
    if (html.length() == 0 && selectedName != null && !selectedName.isBlank()) {
      html.append("<option value='")
          .append(WebHtml.escapeHtml(selectedName))
          .append("' selected>")
          .append(WebHtml.escapeHtml(selectedName))
          .append("</option>");
    }
    return html.toString();
  }

  private static String renderArSelect(String name, String selected) {
    String current =
        selected == null || selected.isBlank() ? "STRAIGHT" : selected.toUpperCase(Locale.ROOT);
    StringBuilder html = new StringBuilder("<select name='");
    html.append(WebHtml.escapeHtml(name)).append("'>");
    for (String value : List.of("STRAIGHT", "CHAINS", "BANDS")) {
      html.append("<option value='")
          .append(value)
          .append("'")
          .append(value.equals(current) ? " selected" : "")
          .append(">")
          .append(value)
          .append("</option>");
    }
    html.append("</select>");
    return html.toString();
  }
}
