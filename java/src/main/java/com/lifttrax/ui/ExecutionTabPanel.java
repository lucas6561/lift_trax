package com.lifttrax.ui;

import com.lifttrax.db.Database;
import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.LiftRegion;
import com.lifttrax.models.LiftType;
import com.lifttrax.models.Muscle;
import com.lifttrax.models.SetMetric;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class ExecutionTabPanel extends JPanel {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final Database db;

    private final JTextField liftNameFilterField = new JTextField(18);
    private final JComboBox<FilterOption<LiftRegion>> regionFilter;
    private final JComboBox<FilterOption<LiftType>> typeFilter;
    private final JComboBox<FilterOption<Muscle>> muscleFilter;

    private final JPanel liftsPanel = new JPanel();

    public ExecutionTabPanel(Database db) {
        this.db = db;
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        regionFilter = createEnumFilter("All Regions", LiftRegion.values());
        typeFilter = createEnumFilter("All Types", LiftType.values());
        muscleFilter = createEnumFilter("All Muscles", Muscle.values());

        add(createFilterPanel(), BorderLayout.NORTH);

        liftsPanel.setLayout(new BoxLayout(liftsPanel, BoxLayout.Y_AXIS));
        JScrollPane scrollPane = new JScrollPane(liftsPanel);
        add(scrollPane, BorderLayout.CENTER);

        refreshLifts();
    }

    private JPanel createFilterPanel() {
        JPanel panel = new JPanel();

        panel.add(new JLabel("Lift:"));
        panel.add(liftNameFilterField);

        panel.add(new JLabel("Region:"));
        panel.add(regionFilter);

        panel.add(new JLabel("Type:"));
        panel.add(typeFilter);

        panel.add(new JLabel("Muscle:"));
        panel.add(muscleFilter);

        liftNameFilterField.getDocument().addDocumentListener(SimpleDocumentListener.of(this::refreshLifts));
        regionFilter.addActionListener(e -> refreshLifts());
        typeFilter.addActionListener(e -> refreshLifts());
        muscleFilter.addActionListener(e -> refreshLifts());

        return panel;
    }

    private void refreshLifts() {
        liftsPanel.removeAll();

        try {
            List<Lift> lifts = db.listLifts();
            List<Lift> filteredLifts = lifts.stream()
                    .filter(this::matchesFilters)
                    .collect(Collectors.toList());

            if (filteredLifts.isEmpty()) {
                JLabel empty = new JLabel("No lifts match the current filters.", SwingConstants.LEFT);
                empty.setAlignmentX(Component.LEFT_ALIGNMENT);
                liftsPanel.add(empty);
            } else {
                for (Lift lift : filteredLifts) {
                    liftsPanel.add(createLiftPanel(lift));
                }
            }
        } catch (Exception error) {
            JLabel errorLabel = new JLabel("Failed to load lifts: " + error.getMessage(), SwingConstants.LEFT);
            errorLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            liftsPanel.add(errorLabel);
        }

        liftsPanel.revalidate();
        liftsPanel.repaint();
    }

    private boolean matchesFilters(Lift lift) {
        String nameFilter = liftNameFilterField.getText().trim().toLowerCase(Locale.ROOT);
        if (!nameFilter.isEmpty() && !lift.name().toLowerCase(Locale.ROOT).contains(nameFilter)) {
            return false;
        }

        LiftRegion selectedRegion = getSelectedValue(regionFilter);
        if (selectedRegion != null && lift.region() != selectedRegion) {
            return false;
        }

        LiftType selectedType = getSelectedValue(typeFilter);
        if (selectedType != null && lift.main() != selectedType) {
            return false;
        }

        Muscle selectedMuscle = getSelectedValue(muscleFilter);
        if (selectedMuscle != null && !lift.muscles().contains(selectedMuscle)) {
            return false;
        }

        return true;
    }

    private JPanel createLiftPanel(Lift lift) {
        String title = lift.name();
        if (!lift.muscles().isEmpty()) {
            String muscles = lift.muscles().stream().map(Enum::name).collect(Collectors.joining(", "));
            title = title + " [" + muscles + "]";
        }

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JLabel meta = new JLabel(buildLiftMeta(lift));
        meta.setFont(meta.getFont().deriveFont(Font.PLAIN));
        meta.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(meta);

        try {
            List<LiftExecution> executions = db.getExecutions(lift.name());
            if (executions.isEmpty()) {
                JLabel empty = new JLabel("No records");
                empty.setAlignmentX(Component.LEFT_ALIGNMENT);
                body.add(empty);
            } else {
                for (LiftExecution execution : executions) {
                    JLabel line = new JLabel(formatExecution(execution));
                    line.setAlignmentX(Component.LEFT_ALIGNMENT);
                    body.add(line);
                }
            }
        } catch (Exception error) {
            JLabel dbError = new JLabel("Failed to load executions: " + error.getMessage());
            dbError.setAlignmentX(Component.LEFT_ALIGNMENT);
            body.add(dbError);
        }

        CollapsiblePanel panel = new CollapsiblePanel(title, body);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        return panel;
    }

    private static String buildLiftMeta(Lift lift) {
        StringBuilder builder = new StringBuilder();
        builder.append("Region: ").append(lift.region());
        if (lift.main() != null) {
            builder.append(" | Type: ").append(lift.main());
        }
        if (lift.notes() != null && !lift.notes().isBlank()) {
            builder.append(" | Notes: ").append(lift.notes());
        }
        return builder.toString();
    }

    private static String formatExecution(LiftExecution execution) {
        String setsSummary = execution.sets().stream().map(ExecutionTabPanel::formatSet).collect(Collectors.joining(", "));
        StringBuilder builder = new StringBuilder();
        builder.append("• ")
                .append(execution.date().format(DATE_FORMATTER))
                .append(": ")
                .append(setsSummary);

        if (execution.warmup()) {
            builder.append(" [WARMUP]");
        }
        if (execution.deload()) {
            builder.append(" [DELOAD]");
        }
        if (execution.notes() != null && !execution.notes().isBlank()) {
            builder.append(" — ").append(execution.notes());
        }

        return builder.toString();
    }

    private static String formatSet(ExecutionSet set) {
        String metric = switch (set.metric()) {
            case SetMetric.Reps reps -> reps.reps() + " reps";
            case SetMetric.RepsLr repsLr -> repsLr.left() + "/" + repsLr.right() + " reps";
            case SetMetric.RepsRange repsRange -> repsRange.min() + "-" + repsRange.max() + " reps";
            case SetMetric.TimeSecs timeSecs -> timeSecs.seconds() + " sec";
            case SetMetric.DistanceFeet distanceFeet -> distanceFeet.feet() + " ft";
        };

        StringBuilder builder = new StringBuilder(metric);
        builder.append(" @ ").append(set.weight());
        if (set.rpe() != null) {
            builder.append(" RPE ").append(set.rpe());
        }
        return builder.toString();
    }

    private static <E extends Enum<E>> JComboBox<FilterOption<E>> createEnumFilter(String allLabel, E[] values) {
        JComboBox<FilterOption<E>> combo = new JComboBox<>();
        combo.addItem(new FilterOption<>(allLabel, null));
        Arrays.stream(values).forEach(v -> combo.addItem(new FilterOption<>(v.name(), v)));
        return combo;
    }

    private static <T> T getSelectedValue(JComboBox<FilterOption<T>> comboBox) {
        FilterOption<T> selected = (FilterOption<T>) comboBox.getSelectedItem();
        return selected == null ? null : selected.value();
    }

    private record FilterOption<T>(String label, T value) {
        @Override
        public String toString() {
            return label;
        }
    }
}
