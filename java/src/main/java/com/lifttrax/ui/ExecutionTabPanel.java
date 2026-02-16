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
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

final class ExecutionTabPanel extends JPanel {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final Database database;
    private final JComboBox<FilterOption<LiftRegion>> regionFilter;
    private final JComboBox<FilterOption<LiftType>> typeFilter;
    private final JList<Muscle> muscleFilter;
    private final JPanel liftsContainer;

    private List<Lift> allLifts = new ArrayList<>();

    ExecutionTabPanel(Database database) {
        super(new BorderLayout());
        this.database = database;

        JPanel filters = new JPanel();
        filters.setLayout(new BoxLayout(filters, BoxLayout.X_AXIS));
        filters.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        regionFilter = new JComboBox<>(buildRegionOptions());
        regionFilter.setMaximumSize(new Dimension(180, 28));

        typeFilter = new JComboBox<>(buildTypeOptions());
        typeFilter.setMaximumSize(new Dimension(220, 28));

        DefaultListModel<Muscle> muscleModel = new DefaultListModel<>();
        Arrays.stream(Muscle.values())
                .sorted(Comparator.comparing(this::toDisplayLabel))
                .forEach(muscleModel::addElement);
        muscleFilter = new JList<>(muscleModel);
        muscleFilter.setVisibleRowCount(4);
        muscleFilter.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        muscleFilter.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> list,
                    Object value,
                    int index,
                    boolean isSelected,
                    boolean cellHasFocus
            ) {
                JLabel label = (JLabel) super.getListCellRendererComponent(
                        list,
                        value,
                        index,
                        isSelected,
                        cellHasFocus
                );
                if (value instanceof Muscle muscle) {
                    label.setText(toDisplayLabel(muscle));
                }
                return label;
            }
        });

        JButton applyFilters = new JButton("Apply Filters");
        applyFilters.addActionListener(_ -> refreshView());
        JButton clearFilters = new JButton("Clear Filters");
        clearFilters.addActionListener(_ -> {
            regionFilter.setSelectedIndex(0);
            typeFilter.setSelectedIndex(0);
            muscleFilter.clearSelection();
            refreshView();
        });

        filters.add(new JLabel("Region:"));
        filters.add(Box.createHorizontalStrut(8));
        filters.add(regionFilter);
        filters.add(Box.createHorizontalStrut(16));
        filters.add(new JLabel("Type:"));
        filters.add(Box.createHorizontalStrut(8));
        filters.add(typeFilter);
        filters.add(Box.createHorizontalStrut(16));
        filters.add(new JLabel("Muscles:"));
        filters.add(Box.createHorizontalStrut(8));
        filters.add(new JScrollPane(muscleFilter));
        filters.add(Box.createHorizontalStrut(12));
        filters.add(applyFilters);
        filters.add(Box.createHorizontalStrut(8));
        filters.add(clearFilters);

        liftsContainer = new JPanel();
        liftsContainer.setLayout(new BoxLayout(liftsContainer, BoxLayout.Y_AXIS));
        liftsContainer.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        add(filters, BorderLayout.NORTH);
        add(new JScrollPane(liftsContainer), BorderLayout.CENTER);

        reloadData();
        refreshView();
    }

    private void reloadData() {
        try {
            allLifts = database.listLifts();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load lifts", e);
        }
    }

    private void refreshView() {
        liftsContainer.removeAll();

        List<Lift> filtered = allLifts.stream()
                .filter(this::matchesRegion)
                .filter(this::matchesType)
                .filter(this::matchesMuscles)
                .toList();

        if (filtered.isEmpty()) {
            liftsContainer.add(new JLabel("No lifts found for current filters."));
        } else {
            for (Lift lift : filtered) {
                liftsContainer.add(buildLiftPanel(lift));
                liftsContainer.add(Box.createVerticalStrut(8));
            }
        }

        liftsContainer.revalidate();
        liftsContainer.repaint();
    }

    private JPanel buildLiftPanel(Lift lift) {
        JPanel wrapper = new JPanel(new BorderLayout());

        String muscles = lift.muscles().stream()
                .map(this::toDisplayLabel)
                .collect(Collectors.joining(", "));
        String subtitle = muscles.isBlank() ? "" : " [" + muscles + "]";
        String main = lift.main() == null ? "" : " • " + toDisplayLabel(lift.main());

        JButton toggle = new JButton("▸ " + lift.name() + subtitle + main);
        toggle.setHorizontalAlignment(JButton.LEFT);
        toggle.setFocusPainted(false);
        toggle.setBorderPainted(false);
        toggle.setContentAreaFilled(false);

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 0));
        body.setVisible(false);

        try {
            List<LiftExecution> executions = database.getExecutions(lift.name());
            if (executions.isEmpty()) {
                body.add(new JLabel("No records"));
            } else {
                for (LiftExecution execution : executions) {
                    body.add(new JLabel(formatExecution(execution)));
                    body.add(Box.createVerticalStrut(4));
                }
            }
        } catch (Exception e) {
            body.add(new JLabel("Failed to load executions: " + e.getMessage()));
        }

        toggle.addActionListener(_ -> {
            boolean show = !body.isVisible();
            body.setVisible(show);
            toggle.setText((show ? "▾ " : "▸ ") + lift.name() + subtitle + main);
            revalidate();
        });

        wrapper.add(toggle, BorderLayout.NORTH);
        wrapper.add(body, BorderLayout.CENTER);
        return wrapper;
    }

    private String formatExecution(LiftExecution execution) {
        String sets = execution.sets().stream()
                .map(this::formatSet)
                .collect(Collectors.joining(" | "));

        String tags = "";
        if (execution.warmup()) {
            tags += " [warmup]";
        }
        if (execution.deload()) {
            tags += " [deload]";
        }

        String notes = (execution.notes() == null || execution.notes().isBlank()) ? "" : " — " + execution.notes();
        return execution.date().format(DATE_FORMAT) + ": " + sets + tags + notes;
    }

    private String formatSet(ExecutionSet set) {
        String rpe = set.rpe() == null ? "" : " RPE " + trimTrailingZero(set.rpe());
        return formatMetric(set.metric()) + " @ " + set.weight() + rpe;
    }

    private String formatMetric(SetMetric metric) {
        if (metric instanceof SetMetric.Reps reps) {
            return reps.reps() + " reps";
        }
        if (metric instanceof SetMetric.RepsLr repsLr) {
            return repsLr.left() + "L/" + repsLr.right() + "R reps";
        }
        if (metric instanceof SetMetric.RepsRange range) {
            return range.min() + "-" + range.max() + " reps";
        }
        if (metric instanceof SetMetric.TimeSecs timeSecs) {
            return timeSecs.seconds() + " sec";
        }
        if (metric instanceof SetMetric.DistanceFeet distanceFeet) {
            return distanceFeet.feet() + " ft";
        }
        return "unknown";
    }

    private boolean matchesRegion(Lift lift) {
        FilterOption<LiftRegion> selected = (FilterOption<LiftRegion>) regionFilter.getSelectedItem();
        return selected == null || selected.value() == null || lift.region() == selected.value();
    }

    private boolean matchesType(Lift lift) {
        FilterOption<LiftType> selected = (FilterOption<LiftType>) typeFilter.getSelectedItem();
        return selected == null || selected.value() == null || lift.main() == selected.value();
    }

    private boolean matchesMuscles(Lift lift) {
        List<Muscle> selected = muscleFilter.getSelectedValuesList();
        return selected.isEmpty() || selected.stream().allMatch(lift.muscles()::contains);
    }

    @SuppressWarnings("unchecked")
    private FilterOption<LiftRegion>[] buildRegionOptions() {
        return new FilterOption[]{
                new FilterOption<>("All", null),
                new FilterOption<>("Upper", LiftRegion.UPPER),
                new FilterOption<>("Lower", LiftRegion.LOWER)
        };
    }

    private FilterOption<LiftType>[] buildTypeOptions() {
        List<FilterOption<LiftType>> options = new ArrayList<>();
        options.add(new FilterOption<>("All", null));
        Arrays.stream(LiftType.values())
                .sorted(Comparator.comparing(this::toDisplayLabel))
                .forEach(type -> options.add(new FilterOption<>(toDisplayLabel(type), type)));
        return options.toArray(new FilterOption[0]);
    }

    private String toDisplayLabel(Enum<?> value) {
        String lower = value.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        String[] parts = lower.split(" ");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    private String trimTrailingZero(float value) {
        if (value == (long) value) {
            return String.format(Locale.ROOT, "%d", (long) value);
        }
        return String.format(Locale.ROOT, "%s", value);
    }

    private record FilterOption<T>(String label, T value) {
        @Override
        public String toString() {
            return label;
        }
    }
}
