package com.lifttrax.ui;

import com.lifttrax.db.Database;
import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.LiftStats;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

final class QueryTabPanel extends JPanel {
    private final Database database;
    private final LiftFilterPanel filterPanel;
    private final JComboBox<String> liftSelector;
    private final JTextArea output;

    private List<Lift> allLifts = new ArrayList<>();
    private List<Lift> filteredLifts = new ArrayList<>();

    QueryTabPanel(Database database) {
        super(new BorderLayout());
        this.database = database;

        filterPanel = new LiftFilterPanel(this::refreshView);

        JPanel selectorPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        selectorPanel.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        selectorPanel.add(new JLabel("Lift:"));

        liftSelector = new JComboBox<>();
        liftSelector.setPrototypeDisplayValue("Select a lift........................");
        liftSelector.addActionListener(event -> refreshOutput());
        selectorPanel.add(liftSelector);

        output = new JTextArea();
        output.setEditable(false);
        output.setLineWrap(true);
        output.setWrapStyleWord(true);
        output.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel top = new JPanel(new BorderLayout());
        top.add(filterPanel, BorderLayout.NORTH);
        top.add(selectorPanel, BorderLayout.SOUTH);

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(output), BorderLayout.CENTER);

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
        filteredLifts = filterPanel.apply(allLifts).stream()
                .sorted(Comparator.comparing(Lift::name))
                .toList();

        String previousSelection = (String) liftSelector.getSelectedItem();
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        filteredLifts.stream().map(Lift::name).forEach(model::addElement);
        liftSelector.setModel(model);

        if (previousSelection != null) {
            liftSelector.setSelectedItem(previousSelection);
        }
        if (liftSelector.getSelectedIndex() < 0 && model.getSize() > 0) {
            liftSelector.setSelectedIndex(0);
        }

        refreshOutput();
    }

    private void refreshOutput() {
        String liftName = (String) liftSelector.getSelectedItem();
        if (liftName == null || liftName.isBlank()) {
            output.setText("No lift selected.");
            return;
        }

        StringBuilder text = new StringBuilder();

        text.append("Last Year\n");
        text.append("========\n");
        try {
            LocalDate oneYearAgo = LocalDate.now().minusDays(365);
            List<LiftExecution> recentExecutions = database.getExecutions(liftName).stream()
                    .filter(exec -> !exec.date().isBefore(oneYearAgo))
                    .toList();

            if (recentExecutions.isEmpty()) {
                text.append("no records\n");
            } else {
                for (LiftExecution execution : recentExecutions) {
                    text.append(ExecutionFormatter.formatExecution(execution))
                            .append("\n");
                }
            }
        } catch (Exception e) {
            text.append("Failed to load last-year data: ").append(e.getMessage()).append("\n");
        }

        text.append("\nBest by reps\n");
        text.append("============\n");
        try {
            LiftStats stats = database.liftStats(liftName);
            if (stats.bestByReps().isEmpty()) {
                text.append("no records\n");
            } else {
                stats.bestByReps().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(entry -> text
                                .append(entry.getKey())
                                .append(" reps: ")
                                .append(entry.getValue())
                                .append("\n"));
            }
        } catch (UnsupportedOperationException e) {
            text.append("Not available in the Java port yet.\n");
        } catch (Exception e) {
            text.append("Failed to load best-by-reps data: ").append(e.getMessage()).append("\n");
        }

        output.setText(text.toString());
        output.setCaretPosition(0);
    }
}
