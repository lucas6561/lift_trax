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
    private final Runnable onAdd;
    private final JComboBox<Lift> liftBox = new JComboBox<>();
    private final JTextField dateField = new JTextField(10);

    // simple mode fields
    private final JTextField weightField = new JTextField(5);
    private final JTextField repsField = new JTextField(5);
    private final JTextField setsField = new JTextField(5);
    private final JTextField rpeField = new JTextField(5);

    // detailed mode fields
    private final JTextField dWeightField = new JTextField(5);
    private final JTextField dRepsField = new JTextField(5);
    private final JTextField dRpeField = new JTextField(5);
    private final DefaultListModel<ExecutionSet> dModel = new DefaultListModel<>();
    private final JList<ExecutionSet> dList = new JList<>(dModel);

    private final JCheckBox warmupCheck = new JCheckBox("Warm-up");
    private final JTextField notesField = new JTextField(20);

    private final CardLayout modeLayout = new CardLayout();
    private final JPanel modePanel = new JPanel(modeLayout);
    private final JRadioButton simpleBtn = new JRadioButton("Sets x Reps", true);
    private final JRadioButton detailedBtn = new JRadioButton("Individual Sets");

    public AddPanel(Database db, Runnable onAdd) {
        this.db = db;
        this.onAdd = onAdd;
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

        c.gridx = 0; c.gridy = row; add(new JLabel("Set Entry Mode:"), c);
        JPanel modeSelect = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        ButtonGroup group = new ButtonGroup();
        group.add(simpleBtn);
        group.add(detailedBtn);
        modeSelect.add(simpleBtn);
        modeSelect.add(detailedBtn);
        c.gridx = 1; add(modeSelect, c); row++;

        modePanel.add(createSimplePanel(), "simple");
        modePanel.add(createDetailedPanel(), "detailed");
        c.gridx = 0; c.gridy = row; c.gridwidth = 2; add(modePanel, c); row++;

        simpleBtn.addActionListener(e -> modeLayout.show(modePanel, "simple"));
        detailedBtn.addActionListener(e -> modeLayout.show(modePanel, "detailed"));

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
            List<ExecutionSet> execSets = new ArrayList<>();
            if (simpleBtn.isSelected()) {
                String weight = weightField.getText().trim();
                int reps = Integer.parseInt(repsField.getText().trim());
                int sets = Integer.parseInt(setsField.getText().trim());
                Double rpe = rpeField.getText().isBlank() ? null : Double.parseDouble(rpeField.getText().trim());
                for (int i = 0; i < sets; i++) {
                    execSets.add(new ExecutionSet(reps, weight, rpe));
                }
            } else {
                if (dModel.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "Add at least one set", "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                for (int i = 0; i < dModel.getSize(); i++) {
                    execSets.add(dModel.getElementAt(i));
                }
            }
            LiftExecution exec = new LiftExecution(null, date, execSets, warmupCheck.isSelected(), notesField.getText().trim());
            db.addLiftExecution(lift.name, exec);
            JOptionPane.showMessageDialog(this, "Execution added", "Success", JOptionPane.INFORMATION_MESSAGE);
            if (onAdd != null) {
                onAdd.run();
            }
            if (!simpleBtn.isSelected()) {
                dModel.clear();
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private JPanel createSimplePanel() {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        int row = 0;
        c.gridx = 0; c.gridy = row; p.add(new JLabel("Weight:"), c);
        c.gridx = 1; p.add(weightField, c); row++;
        c.gridx = 0; c.gridy = row; p.add(new JLabel("Reps:"), c);
        c.gridx = 1; p.add(repsField, c); row++;
        c.gridx = 0; c.gridy = row; p.add(new JLabel("Sets:"), c);
        c.gridx = 1; p.add(setsField, c); row++;
        c.gridx = 0; c.gridy = row; p.add(new JLabel("RPE:"), c);
        c.gridx = 1; p.add(rpeField, c);
        return p;
    }

    private JPanel createDetailedPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        int row = 0;
        c.gridx = 0; c.gridy = row; p.add(new JLabel("Weight:"), c);
        c.gridx = 1; p.add(dWeightField, c); row++;
        c.gridx = 0; c.gridy = row; p.add(new JLabel("Reps:"), c);
        c.gridx = 1; p.add(dRepsField, c); row++;
        c.gridx = 0; c.gridy = row; p.add(new JLabel("RPE:"), c);
        JPanel rpePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        rpePanel.add(dRpeField);
        JButton addSet = new JButton("Add Set");
        addSet.addActionListener(e -> addDetailedSet());
        rpePanel.add(addSet);
        c.gridx = 1; p.add(rpePanel, c); row++;
        dList.setVisibleRowCount(5);
        dList.setCellRenderer(new SetRenderer());
        c.gridx = 0; c.gridy = row; c.gridwidth = 2; p.add(new JScrollPane(dList), c); row++;
        JButton remove = new JButton("Remove Selected");
        remove.addActionListener(e -> {
            int idx = dList.getSelectedIndex();
            if (idx >= 0) dModel.remove(idx);
        });
        c.gridx = 1; c.gridy = row; c.gridwidth = 1; p.add(remove, c);
        return p;
    }

    private void addDetailedSet() {
        try {
            String weight = dWeightField.getText().trim();
            int reps = Integer.parseInt(dRepsField.getText().trim());
            Double rpe = dRpeField.getText().isBlank() ? null : Double.parseDouble(dRpeField.getText().trim());
            dModel.addElement(new ExecutionSet(reps, weight, rpe));
            dWeightField.setText("");
            dRepsField.setText("");
            dRpeField.setText("");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static class SetRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof ExecutionSet set) {
                StringBuilder sb = new StringBuilder();
                sb.append(set.displayWeight());
                if (set.reps != null) {
                    sb.append(" x ").append(set.reps);
                }
                if (set.rpe != null) {
                    sb.append(" @ RPE ").append(set.rpe);
                }
                label.setText(sb.toString());
            }
            return label;
        }
    }
}
