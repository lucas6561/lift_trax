package com.lifttrax.ui;

import com.lifttrax.db.Database;
import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.SetMetric;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

final class ExecutionTabPanel extends JPanel {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final Database database;
    private final LiftFilterPanel filterPanel;
    private final JPanel liftsContainer;

    private List<Lift> allLifts = new ArrayList<>();

    ExecutionTabPanel(Database database) {
        super(new BorderLayout());
        this.database = database;

        filterPanel = new LiftFilterPanel(this::refreshView);

        liftsContainer = new JPanel();
        liftsContainer.setLayout(new BoxLayout(liftsContainer, BoxLayout.Y_AXIS));
        liftsContainer.setBorder(BorderFactory.createEmptyBorder(6, 8, 8, 8));

        add(filterPanel, BorderLayout.NORTH);
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

        List<Lift> filtered = filterPanel.apply(allLifts);

        if (filtered.isEmpty()) {
            liftsContainer.add(new JLabel("No lifts found for current filters."));
        } else {
            for (Lift lift : filtered) {
                liftsContainer.add(buildLiftPanel(lift));
                liftsContainer.add(Box.createVerticalStrut(6));
            }
        }

        liftsContainer.revalidate();
        liftsContainer.repaint();
    }

    private JPanel buildLiftPanel(Lift lift) {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new java.awt.Color(56, 62, 70)),
                BorderFactory.createEmptyBorder(2, 4, 4, 4)
        ));

        String muscles = lift.muscles().stream()
                .map(LiftFilterPanel::toDisplayLabel)
                .collect(Collectors.joining(", "));
        String subtitle = muscles.isBlank() ? "" : " [" + muscles + "]";
        String main = lift.main() == null ? "" : " • " + LiftFilterPanel.toDisplayLabel(lift.main());

        JButton toggle = new JButton("▸ " + lift.name() + subtitle + main);
        toggle.setHorizontalAlignment(JButton.LEFT);
        toggle.setFocusPainted(false);
        toggle.setBorderPainted(false);
        toggle.setContentAreaFilled(false);

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(BorderFactory.createEmptyBorder(2, 14, 2, 2));
        body.setVisible(false);

        try {
            List<LiftExecution> executions = database.getExecutions(lift.name());
            if (executions.isEmpty()) {
                body.add(new JLabel("No records"));
            } else {
                for (LiftExecution execution : executions) {
                    body.add(new JLabel(formatExecution(execution)));
                    body.add(Box.createVerticalStrut(3));
                }
            }
        } catch (Exception e) {
            body.add(new JLabel("Failed to load executions: " + e.getMessage()));
        }

        toggle.addActionListener(event -> {
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

    private String trimTrailingZero(float value) {
        if (value == (long) value) {
            return String.format(Locale.ROOT, "%d", (long) value);
        }
        return String.format(Locale.ROOT, "%s", value);
    }
}
