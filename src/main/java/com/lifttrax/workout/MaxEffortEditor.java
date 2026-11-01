package com.lifttrax.workout;

import com.lifttrax.models.Lift;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

/** Core MaxEffortEditor component used by LiftTrax. */
final class MaxEffortEditor {
  private MaxEffortEditor() {}

  static MaxEffortPlan editPlan(
      List<Lift> squatOptions,
      List<Lift> deadliftOptions,
      List<Lift> benchOptions,
      List<Lift> overheadOptions,
      List<Lift> defaultLower,
      List<Lift> defaultUpper) {
    if (squatOptions.isEmpty()
        || deadliftOptions.isEmpty()
        || benchOptions.isEmpty()
        || overheadOptions.isEmpty()) {
      return MaxEffortPlan.fromDefaults(defaultLower, defaultUpper);
    }
    if (GraphicsEnvironment.isHeadless() || System.getenv("LIFT_TRAX_HEADLESS") != null) {
      return MaxEffortPlan.fromDefaults(defaultLower, defaultUpper);
    }
    return editPlanInteractively(
        squatOptions,
        deadliftOptions,
        benchOptions,
        overheadOptions,
        defaultLower,
        defaultUpper,
        (panel, title) ->
            JOptionPane.showConfirmDialog(
                null, panel, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE));
  }

  static MaxEffortPlan editPlanInteractively(
      List<Lift> squatOptions,
      List<Lift> deadliftOptions,
      List<Lift> benchOptions,
      List<Lift> overheadOptions,
      List<Lift> defaultLower,
      List<Lift> defaultUpper,
      ConfirmationDialog dialog) {
    int weeks = defaultLower.size();
    List<Integer> lowerEvenWeeks = weekIndexes(weeks, 0);
    List<Integer> lowerOddWeeks = weekIndexes(weeks, 1);
    List<JComboBox<Lift>> squatWeekCombos =
        combosForWeeks(squatOptions, defaultLower, lowerEvenWeeks);
    List<JComboBox<Lift>> deadliftWeekCombos =
        combosForWeeks(deadliftOptions, defaultLower, lowerOddWeeks);
    List<JComboBox<Lift>> benchWeekCombos =
        combosForWeeks(benchOptions, defaultUpper, lowerEvenWeeks);
    List<JComboBox<Lift>> ohpWeekCombos =
        combosForWeeks(overheadOptions, defaultUpper, lowerOddWeeks);
    EditorPanel editor =
        editorPanel(
            squatOptions,
            deadliftOptions,
            benchOptions,
            overheadOptions,
            defaultLower,
            defaultUpper,
            lowerEvenWeeks,
            lowerOddWeeks,
            squatWeekCombos,
            deadliftWeekCombos,
            benchWeekCombos,
            ohpWeekCombos);
    if (dialog.show(editor.panel(), "Max Effort Planner") != JOptionPane.OK_OPTION) {
      return MaxEffortPlan.fromDefaults(defaultLower, defaultUpper);
    }
    List<Lift> lower = buildWeekPlan(defaultLower, squatWeekCombos, deadliftWeekCombos);
    List<Lift> upper = buildWeekPlan(defaultUpper, benchWeekCombos, ohpWeekCombos);
    return selectedPlan(lower, upper, editor);
  }

  private static EditorPanel editorPanel(
      List<Lift> squatOptions,
      List<Lift> deadliftOptions,
      List<Lift> benchOptions,
      List<Lift> overheadOptions,
      List<Lift> defaultLower,
      List<Lift> defaultUpper,
      List<Integer> lowerEvenWeeks,
      List<Integer> lowerOddWeeks,
      List<JComboBox<Lift>> squatWeekCombos,
      List<JComboBox<Lift>> deadliftWeekCombos,
      List<JComboBox<Lift>> benchWeekCombos,
      List<JComboBox<Lift>> ohpWeekCombos) {
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    panel.add(columnPanel("Squat Weeks", squatWeekCombos, lowerEvenWeeks));
    panel.add(columnPanel("Deadlift Weeks", deadliftWeekCombos, lowerOddWeeks));
    panel.add(columnPanel("Bench Weeks", benchWeekCombos, lowerEvenWeeks));
    panel.add(columnPanel("Overhead Weeks", ohpWeekCombos, lowerOddWeeks));
    List<JComboBox<Lift>> lowerSquat = new ArrayList<>();
    List<JComboBox<Lift>> lowerDeadlift = new ArrayList<>();
    List<JComboBox<Lift>> upperBench = new ArrayList<>();
    List<JComboBox<Lift>> upperOhp = new ArrayList<>();
    addDeloadHeading(panel, defaultLower, defaultUpper);
    addLowerDeloadRows(
        panel, squatOptions, deadliftOptions, defaultLower, lowerSquat, lowerDeadlift);
    addUpperDeloadRows(panel, benchOptions, overheadOptions, defaultUpper, upperBench, upperOhp);
    return new EditorPanel(panel, lowerSquat, lowerDeadlift, upperBench, upperOhp);
  }

  private static void addDeloadHeading(
      JPanel panel, List<Lift> defaultLower, List<Lift> defaultUpper) {
    if (!MaxEffortPlan.deriveLowerDeloadFromPlan(defaultLower).isEmpty()
        || !MaxEffortPlan.deriveUpperDeloadFromPlan(defaultUpper).isEmpty()) {
      panel.add(new JLabel("Deload Weeks"));
    }
  }

  private static void addLowerDeloadRows(
      JPanel panel,
      List<Lift> squatOptions,
      List<Lift> deadliftOptions,
      List<Lift> defaults,
      List<JComboBox<Lift>> squats,
      List<JComboBox<Lift>> deadlifts) {
    List<MaxEffortPlan.DeloadLowerLifts> deloads =
        MaxEffortPlan.deriveLowerDeloadFromPlan(defaults);
    for (int i = 0; i < deloads.size(); i++) {
      var selected = deloads.get(i);
      JComboBox<Lift> squat = selectedCombo(squatOptions, selected.squat());
      JComboBox<Lift> deadlift = selectedCombo(deadliftOptions, selected.deadlift());
      squats.add(squat);
      deadlifts.add(deadlift);
      panel.add(row("Lower Week " + ((i + 1) * 7), "Squat", squat, "Deadlift", deadlift));
    }
  }

  private static void addUpperDeloadRows(
      JPanel panel,
      List<Lift> benchOptions,
      List<Lift> overheadOptions,
      List<Lift> defaults,
      List<JComboBox<Lift>> benches,
      List<JComboBox<Lift>> overheads) {
    List<MaxEffortPlan.DeloadUpperLifts> deloads =
        MaxEffortPlan.deriveUpperDeloadFromPlan(defaults);
    for (int i = 0; i < deloads.size(); i++) {
      var selected = deloads.get(i);
      JComboBox<Lift> bench = selectedCombo(benchOptions, selected.bench());
      JComboBox<Lift> overhead = selectedCombo(overheadOptions, selected.overhead());
      benches.add(bench);
      overheads.add(overhead);
      panel.add(row("Upper Week " + ((i + 1) * 7), "Bench", bench, "OHP", overhead));
    }
  }

  private static JComboBox<Lift> selectedCombo(List<Lift> options, Lift selected) {
    JComboBox<Lift> combo = new JComboBox<>(options.toArray(Lift[]::new));
    combo.setSelectedItem(selected);
    return combo;
  }

  private static MaxEffortPlan selectedPlan(
      List<Lift> lower, List<Lift> upper, EditorPanel editor) {
    List<MaxEffortPlan.DeloadLowerLifts> lowerDeload = new ArrayList<>();
    for (int i = 0; i < editor.lowerSquat().size(); i++) {
      lowerDeload.add(
          new MaxEffortPlan.DeloadLowerLifts(
              (Lift) editor.lowerSquat().get(i).getSelectedItem(),
              (Lift) editor.lowerDeadlift().get(i).getSelectedItem()));
    }
    List<MaxEffortPlan.DeloadUpperLifts> upperDeload = new ArrayList<>();
    for (int i = 0; i < editor.upperBench().size(); i++) {
      upperDeload.add(
          new MaxEffortPlan.DeloadUpperLifts(
              (Lift) editor.upperBench().get(i).getSelectedItem(),
              (Lift) editor.upperOhp().get(i).getSelectedItem()));
    }
    return new MaxEffortPlan(lower, upper, lowerDeload, upperDeload);
  }

  private record EditorPanel(
      JPanel panel,
      List<JComboBox<Lift>> lowerSquat,
      List<JComboBox<Lift>> lowerDeadlift,
      List<JComboBox<Lift>> upperBench,
      List<JComboBox<Lift>> upperOhp) {}

  @FunctionalInterface
  interface ConfirmationDialog {
    int show(JPanel panel, String title);
  }

  private static JPanel row(
      String title, String aLabel, JComboBox<Lift> a, String bLabel, JComboBox<Lift> b) {
    JPanel panel = new JPanel(new GridLayout(1, 5, 8, 8));
    panel.add(new JLabel(title));
    panel.add(new JLabel(aLabel));
    panel.add(a);
    panel.add(new JLabel(bLabel));
    panel.add(b);
    return panel;
  }

  private static JPanel columnPanel(
      String title, List<JComboBox<Lift>> combos, List<Integer> weekIndexes) {
    JPanel panel = new JPanel(new GridLayout(0, 2, 8, 8));
    panel.add(new JLabel(title));
    panel.add(new JLabel(""));
    for (int i = 0; i < combos.size(); i++) {
      panel.add(new JLabel("Week " + (weekIndexes.get(i) + 1)));
      panel.add(combos.get(i));
    }
    return panel;
  }

  private static List<Integer> weekIndexes(int weeks, int parity) {
    List<Integer> indexes = new ArrayList<>();
    for (int week = 0; week < weeks; week++) {
      if (week % 2 == parity && (week + 1) % 7 != 0) {
        indexes.add(week);
      }
    }
    return indexes;
  }

  private static List<JComboBox<Lift>> combosForWeeks(
      List<Lift> options, List<Lift> defaults, List<Integer> weekIndexes) {
    List<JComboBox<Lift>> combos = new ArrayList<>();
    for (Integer week : weekIndexes) {
      JComboBox<Lift> combo = new JComboBox<>(options.toArray(Lift[]::new));
      combo.setSelectedItem(defaults.get(week));
      combos.add(combo);
    }
    return combos;
  }

  private static List<Lift> buildWeekPlan(
      List<Lift> defaults, List<JComboBox<Lift>> primary, List<JComboBox<Lift>> secondary) {
    List<Lift> plan = new ArrayList<>(defaults.size());
    int p = 0;
    int s = 0;
    for (int week = 0; week < defaults.size(); week++) {
      if ((week + 1) % 7 == 0) {
        plan.add(defaults.get(week));
        continue;
      }
      if (week % 2 == 0) {
        plan.add((Lift) primary.get(p++).getSelectedItem());
      } else {
        plan.add((Lift) secondary.get(s++).getSelectedItem());
      }
    }
    return plan;
  }
}
