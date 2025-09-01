package org.lift.trax.gui;

import org.lift.trax.Database;
import org.lift.trax.Lift;
import org.lift.trax.LiftExecution;
import org.lift.trax.LiftStats;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.util.Map;

/**
 * Panel for querying lift statistics. Allows selection of a lift and displays
 * recent executions along with best weights by rep count.
 */
public class QueryPanel extends JPanel {
    private final Database db;
    private final JComboBox<Lift> liftBox = new JComboBox<>();
    private final JTextArea resultArea = new JTextArea(20, 40);

    public QueryPanel(Database db) {
        this.db = db;
        setLayout(new BorderLayout());

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Lift:"));
        loadLifts();
        liftBox.addActionListener(e -> showStats());
        top.add(liftBox);
        add(top, BorderLayout.NORTH);

        resultArea.setEditable(false);
        add(new JScrollPane(resultArea), BorderLayout.CENTER);
    }

    private void loadLifts() {
        liftBox.removeAllItems();
        try {
            for (Lift lift : db.listLifts(null)) {
                liftBox.addItem(lift);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showStats() {
        Lift lift = (Lift) liftBox.getSelectedItem();
        if (lift == null) {
            resultArea.setText("");
            return;
        }
        try {
            LiftStats stats = db.liftStats(lift.name);
            LocalDate oneYearAgo = LocalDate.now().minusYears(1);
            StringBuilder sb = new StringBuilder();
            sb.append("Last Year:\n");
            boolean any = false;
            for (LiftExecution exec : lift.executions) {
                if (!exec.date.isBefore(oneYearAgo)) {
                    sb.append(exec.toString()).append("\n");
                    any = true;
                }
            }
            if (!any) {
                sb.append("no records\n");
            }
            sb.append("\nBest by reps:\n");
            for (Map.Entry<Integer, Double> entry : stats.bestByReps.entrySet()) {
                sb.append(entry.getKey()).append(" reps: ")
                        .append(entry.getValue()).append("\n");
            }
            resultArea.setText(sb.toString());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}

