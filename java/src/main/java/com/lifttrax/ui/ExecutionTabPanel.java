package com.lifttrax.ui;

import com.lifttrax.db.Database;
import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftExecution;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

final class ExecutionTabPanel extends JPanel {
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
                    body.add(buildExecutionRow(lift, execution));
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

    private JPanel buildExecutionRow(Lift lift, LiftExecution execution) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.add(new JLabel(ExecutionFormatter.formatExecution(execution)), BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JButton editButton = new JButton("Edit");
        JButton deleteButton = new JButton("Delete");
        actions.add(editButton);
        actions.add(deleteButton);
        row.add(actions, BorderLayout.EAST);

        editButton.addActionListener(event -> editExecution(lift, execution));
        deleteButton.addActionListener(event -> deleteExecution(execution));
        return row;
    }

    private void editExecution(Lift lift, LiftExecution execution) {
        if (execution.id() == null) {
            JOptionPane.showMessageDialog(this, "Execution has no ID and cannot be edited.", "Edit Execution", JOptionPane.ERROR_MESSAGE);
            return;
        }

        javax.swing.JTextField dateField = new javax.swing.JTextField(execution.date().toString());
        javax.swing.JTextField notesField = new javax.swing.JTextField(execution.notes());
        JCheckBox warmupBox = new JCheckBox("Warm-up", execution.warmup());
        JCheckBox deloadBox = new JCheckBox("Deload", execution.deload());

        JPanel panel = new JPanel(new GridLayout(0, 1, 0, 6));
        panel.add(new JLabel("Date (YYYY-MM-DD):"));
        panel.add(dateField);
        panel.add(new JLabel("Notes:"));
        panel.add(notesField);
        panel.add(warmupBox);
        panel.add(deloadBox);

        int choice = JOptionPane.showConfirmDialog(
                this,
                panel,
                "Edit Execution: " + lift.name(),
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        if (choice != JOptionPane.OK_OPTION) {
            return;
        }

        try {
            LiftExecution updated = new LiftExecution(
                    execution.id(),
                    java.time.LocalDate.parse(dateField.getText().trim()),
                    execution.sets(),
                    warmupBox.isSelected(),
                    deloadBox.isSelected(),
                    notesField.getText().trim()
            );
            database.updateLiftExecution(execution.id(), updated);
            reloadData();
            refreshView();
        } catch (DateTimeParseException parseException) {
            JOptionPane.showMessageDialog(this, "Invalid date. Use YYYY-MM-DD.", "Edit Execution", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to update execution: " + ex.getMessage(), "Edit Execution", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteExecution(LiftExecution execution) {
        if (execution.id() == null) {
            JOptionPane.showMessageDialog(this, "Execution has no ID and cannot be deleted.", "Delete Execution", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int choice = JOptionPane.showConfirmDialog(
                this,
                "Delete this execution?",
                "Delete Execution",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (choice != JOptionPane.OK_OPTION) {
            return;
        }

        try {
            database.deleteLiftExecution(execution.id());
            reloadData();
            refreshView();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to delete execution: " + ex.getMessage(), "Delete Execution", JOptionPane.ERROR_MESSAGE);
        }
    }
}
