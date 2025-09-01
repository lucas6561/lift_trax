package org.lift.trax.gui;

import org.lift.trax.Database;
import org.lift.trax.ExecutionSet;
import org.lift.trax.Lift;
import org.lift.trax.LiftExecution;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class AddPanel extends JPanel {
    private final Database db;
    private final JComboBox<Lift> liftBox = new JComboBox<>();
    private final JTextField dateField = new JTextField(10);
    private final JTextField weightField = new JTextField(5);
    private final JTextField repsField = new JTextField(5);
    private final JTextField setsField = new JTextField(5);
    private final JTextField rpeField = new JTextField(5);
    private final JCheckBox warmupCheck = new JCheckBox("Warm-up");
    private final JTextField notesField = new JTextField(20);

    public AddPanel(Database db) {
        this.db = db;
        setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        c.gridx = 0; c.gridy = row; add(new JLabel("Lift:"), c);
        c.gridx = 1; loadLifts(); add(liftBox, c); row++;

        c.gridx = 0; c.gridy = row; add(new JLabel("Date (YYYY-MM-DD):"), c);
        c.gridx = 1; dateField.setText(LocalDate.now().toString()); add(dateField, c); row++;

        c.gridx = 0; c.gridy = row; add(new JLabel("Weight:"), c);
        c.gridx = 1; add(weightField, c); row++;

        c.gridx = 0; c.gridy = row; add(new JLabel("Reps:"), c);
        c.gridx = 1; add(repsField, c); row++;

        c.gridx = 0; c.gridy = row; add(new JLabel("Sets:"), c);
        c.gridx = 1; add(setsField, c); row++;

        c.gridx = 0; c.gridy = row; add(new JLabel("RPE:"), c);
        c.gridx = 1; add(rpeField, c); row++;

        c.gridx = 1; c.gridy = row; add(warmupCheck, c); row++;

        c.gridx = 0; c.gridy = row; add(new JLabel("Notes:"), c);
        c.gridx = 1; add(notesField, c); row++;

        JButton addBtn = new JButton("Add Execution");
        addBtn.addActionListener(e -> addExecution());
        c.gridx = 1; c.gridy = row; c.anchor = GridBagConstraints.CENTER;
        add(addBtn, c);
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

    private void addExecution() {
        Lift lift = (Lift) liftBox.getSelectedItem();
        if (lift == null) {
            JOptionPane.showMessageDialog(this, "Select a lift", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        try {
            LocalDate date = LocalDate.parse(dateField.getText().trim());
            double weight = Double.parseDouble(weightField.getText().trim());
            int reps = Integer.parseInt(repsField.getText().trim());
            int sets = Integer.parseInt(setsField.getText().trim());
            Double rpe = rpeField.getText().isBlank() ? null : Double.parseDouble(rpeField.getText().trim());
            List<ExecutionSet> execSets = new ArrayList<>();
            for (int i = 0; i < sets; i++) {
                execSets.add(new ExecutionSet(reps, weight, rpe));
            }
            LiftExecution exec = new LiftExecution(null, date, execSets, warmupCheck.isSelected(), notesField.getText().trim());
            db.addLiftExecution(lift.name, exec);
            JOptionPane.showMessageDialog(this, "Execution added", "Success", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
