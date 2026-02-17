package com.lifttrax.workout;

import com.lifttrax.models.Lift;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.util.List;

final class DynamicLiftSelector {
    private DynamicLiftSelector() {}

    static DynamicLiftChoices choose(
            List<Lift> squatOptions,
            List<Lift> deadliftOptions,
            List<Lift> benchOptions,
            List<Lift> overheadOptions,
            DynamicLiftChoices defaults
    ) {
        if (GraphicsEnvironment.isHeadless() || System.getenv("LIFT_TRAX_HEADLESS") != null) {
            return defaults;
        }

        JComboBox<Lift> squat = new JComboBox<>(squatOptions.toArray(Lift[]::new));
        JComboBox<Lift> deadlift = new JComboBox<>(deadliftOptions.toArray(Lift[]::new));
        JComboBox<Lift> bench = new JComboBox<>(benchOptions.toArray(Lift[]::new));
        JComboBox<Lift> overhead = new JComboBox<>(overheadOptions.toArray(Lift[]::new));

        squat.setSelectedItem(defaults.squat());
        deadlift.setSelectedItem(defaults.deadlift());
        bench.setSelectedItem(defaults.bench());
        overhead.setSelectedItem(defaults.overhead());

        JPanel panel = new JPanel(new GridLayout(0, 2, 8, 8));
        panel.add(new JLabel("Squat"));
        panel.add(squat);
        panel.add(new JLabel("Deadlift"));
        panel.add(deadlift);
        panel.add(new JLabel("Bench Press"));
        panel.add(bench);
        panel.add(new JLabel("Overhead Press"));
        panel.add(overhead);

        int result = JOptionPane.showConfirmDialog(
                null,
                panel,
                "Dynamic Effort Lifts",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        if (result != JOptionPane.OK_OPTION) {
            return defaults;
        }

        return new DynamicLiftChoices(
                (Lift) squat.getSelectedItem(),
                (Lift) deadlift.getSelectedItem(),
                (Lift) bench.getSelectedItem(),
                (Lift) overhead.getSelectedItem()
        );
    }

    record DynamicLiftChoices(Lift squat, Lift deadlift, Lift bench, Lift overhead) {}
}
