package com.lifttrax.workout;

import com.lifttrax.models.Lift;

import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;

final class MaxEffortEditor {
    private MaxEffortEditor() {}

    static MaxEffortPlan editPlan(
            List<Lift> squatOptions,
            List<Lift> deadliftOptions,
            List<Lift> benchOptions,
            List<Lift> overheadOptions,
            List<Lift> defaultLower,
            List<Lift> defaultUpper
    ) {
        if (squatOptions.isEmpty() || deadliftOptions.isEmpty() || benchOptions.isEmpty() || overheadOptions.isEmpty()) {
            return MaxEffortPlan.fromDefaults(defaultLower, defaultUpper);
        }
        if (GraphicsEnvironment.isHeadless() || System.getenv("LIFT_TRAX_HEADLESS") != null) {
            return MaxEffortPlan.fromDefaults(defaultLower, defaultUpper);
        }

        int weeks = defaultLower.size();
        List<Integer> lowerEvenWeeks = weekIndexes(weeks, 0);
        List<Integer> lowerOddWeeks = weekIndexes(weeks, 1);

        List<JComboBox<Lift>> squatWeekCombos = combosForWeeks(squatOptions, defaultLower, lowerEvenWeeks);
        List<JComboBox<Lift>> deadliftWeekCombos = combosForWeeks(deadliftOptions, defaultLower, lowerOddWeeks);
        List<JComboBox<Lift>> benchWeekCombos = combosForWeeks(benchOptions, defaultUpper, lowerEvenWeeks);
        List<JComboBox<Lift>> ohpWeekCombos = combosForWeeks(overheadOptions, defaultUpper, lowerOddWeeks);

        List<MaxEffortPlan.DeloadLowerLifts> lowerDeloadDefaults = MaxEffortPlan.deriveLowerDeloadFromPlan(defaultLower);
        List<MaxEffortPlan.DeloadUpperLifts> upperDeloadDefaults = MaxEffortPlan.deriveUpperDeloadFromPlan(defaultUpper);

        List<JComboBox<Lift>> lowerDeloadSquat = new ArrayList<>();
        List<JComboBox<Lift>> lowerDeloadDeadlift = new ArrayList<>();
        List<JComboBox<Lift>> upperDeloadBench = new ArrayList<>();
        List<JComboBox<Lift>> upperDeloadOhp = new ArrayList<>();

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(columnPanel("Squat Weeks", squatWeekCombos, lowerEvenWeeks));
        panel.add(columnPanel("Deadlift Weeks", deadliftWeekCombos, lowerOddWeeks));
        panel.add(columnPanel("Bench Weeks", benchWeekCombos, lowerEvenWeeks));
        panel.add(columnPanel("Overhead Weeks", ohpWeekCombos, lowerOddWeeks));

        if (!lowerDeloadDefaults.isEmpty() || !upperDeloadDefaults.isEmpty()) {
            panel.add(new JLabel("Deload Weeks"));
        }

        for (int i = 0; i < lowerDeloadDefaults.size(); i++) {
            var def = lowerDeloadDefaults.get(i);
            JComboBox<Lift> squat = new JComboBox<>(squatOptions.toArray(Lift[]::new));
            JComboBox<Lift> dead = new JComboBox<>(deadliftOptions.toArray(Lift[]::new));
            squat.setSelectedItem(def.squat());
            dead.setSelectedItem(def.deadlift());
            lowerDeloadSquat.add(squat);
            lowerDeloadDeadlift.add(dead);
            panel.add(row("Lower Week " + ((i + 1) * 7) + "", "Squat", squat, "Deadlift", dead));
        }

        for (int i = 0; i < upperDeloadDefaults.size(); i++) {
            var def = upperDeloadDefaults.get(i);
            JComboBox<Lift> bench = new JComboBox<>(benchOptions.toArray(Lift[]::new));
            JComboBox<Lift> ohp = new JComboBox<>(overheadOptions.toArray(Lift[]::new));
            bench.setSelectedItem(def.bench());
            ohp.setSelectedItem(def.overhead());
            upperDeloadBench.add(bench);
            upperDeloadOhp.add(ohp);
            panel.add(row("Upper Week " + ((i + 1) * 7) + "", "Bench", bench, "OHP", ohp));
        }

        int result = JOptionPane.showConfirmDialog(
                null,
                panel,
                "Max Effort Planner",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        if (result != JOptionPane.OK_OPTION) {
            return MaxEffortPlan.fromDefaults(defaultLower, defaultUpper);
        }

        List<Lift> lower = buildWeekPlan(defaultLower, squatWeekCombos, deadliftWeekCombos);
        List<Lift> upper = buildWeekPlan(defaultUpper, benchWeekCombos, ohpWeekCombos);

        List<MaxEffortPlan.DeloadLowerLifts> lowerDeload = new ArrayList<>();
        for (int i = 0; i < lowerDeloadSquat.size(); i++) {
            lowerDeload.add(new MaxEffortPlan.DeloadLowerLifts(
                    (Lift) lowerDeloadSquat.get(i).getSelectedItem(),
                    (Lift) lowerDeloadDeadlift.get(i).getSelectedItem()
            ));
        }

        List<MaxEffortPlan.DeloadUpperLifts> upperDeload = new ArrayList<>();
        for (int i = 0; i < upperDeloadBench.size(); i++) {
            upperDeload.add(new MaxEffortPlan.DeloadUpperLifts(
                    (Lift) upperDeloadBench.get(i).getSelectedItem(),
                    (Lift) upperDeloadOhp.get(i).getSelectedItem()
            ));
        }

        return new MaxEffortPlan(lower, upper, lowerDeload, upperDeload);
    }

    private static JPanel row(String title, String aLabel, JComboBox<Lift> a, String bLabel, JComboBox<Lift> b) {
        JPanel panel = new JPanel(new GridLayout(1, 5, 8, 8));
        panel.add(new JLabel(title));
        panel.add(new JLabel(aLabel));
        panel.add(a);
        panel.add(new JLabel(bLabel));
        panel.add(b);
        return panel;
    }

    private static JPanel columnPanel(String title, List<JComboBox<Lift>> combos, List<Integer> weekIndexes) {
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

    private static List<JComboBox<Lift>> combosForWeeks(List<Lift> options, List<Lift> defaults, List<Integer> weekIndexes) {
        List<JComboBox<Lift>> combos = new ArrayList<>();
        for (Integer week : weekIndexes) {
            JComboBox<Lift> combo = new JComboBox<>(options.toArray(Lift[]::new));
            combo.setSelectedItem(defaults.get(week));
            combos.add(combo);
        }
        return combos;
    }

    private static List<Lift> buildWeekPlan(List<Lift> defaults, List<JComboBox<Lift>> primary, List<JComboBox<Lift>> secondary) {
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
