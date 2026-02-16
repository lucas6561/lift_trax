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
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class AddExecutionTabPanel extends JPanel {
    private final Database database;

    private final JComboBox<String> liftCombo;
    private final JTextField weightField;
    private final JComboBox<MetricInputMode> metricModeCombo;
    private final JComboBox<SetInputMode> setModeCombo;
    private final JTextField setsField;
    private final JTextField valueField;
    private final JTextField leftValueField;
    private final JTextField rightValueField;
    private final JTextField rpeField;
    private final JCheckBox warmupCheck;
    private final JCheckBox deloadCheck;
    private final JTextField dateField;
    private final JTextField notesField;
    private final JLabel statusLabel;

    private final DefaultListModel<String> detailedSetsModel;
    private final List<ExecutionSet> detailedSets;

    private final JPanel newLiftPanel;
    private JTextField newLiftName;
    private JComboBox<LiftRegion> newLiftRegion;
    private JComboBox<LiftType> newLiftType;
    private JList<Muscle> newLiftMuscles;
    private JTextField newLiftNotes;

    private List<Lift> lifts = new ArrayList<>();

    AddExecutionTabPanel(Database database) {
        super(new BorderLayout());
        this.database = database;

        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel liftRow = new JPanel();
        liftRow.setLayout(new BoxLayout(liftRow, BoxLayout.X_AXIS));
        liftCombo = new JComboBox<>();
        liftCombo.setMaximumSize(new Dimension(320, 30));
        JButton refreshButton = new JButton("Refresh");
        JButton loadLastButton = new JButton("Load Last Execution");
        JButton newLiftButton = new JButton("New Lift");
        liftRow.add(new JLabel("Lift:"));
        liftRow.add(Box.createHorizontalStrut(8));
        liftRow.add(liftCombo);
        liftRow.add(Box.createHorizontalStrut(8));
        liftRow.add(refreshButton);
        liftRow.add(Box.createHorizontalStrut(8));
        liftRow.add(loadLastButton);
        liftRow.add(Box.createHorizontalStrut(8));
        liftRow.add(newLiftButton);

        JPanel weightRow = row("Weight:");
        weightField = new JTextField();
        weightField.setMaximumSize(new Dimension(180, 30));
        weightRow.add(weightField);

        JPanel setModeRow = row("Set Input:");
        setModeCombo = new JComboBox<>(SetInputMode.values());
        setModeCombo.setMaximumSize(new Dimension(220, 30));
        setModeRow.add(setModeCombo);

        JPanel metricRow = row("Metric:");
        metricModeCombo = new JComboBox<>(MetricInputMode.values());
        metricModeCombo.setMaximumSize(new Dimension(220, 30));
        metricRow.add(metricModeCombo);

        JPanel setsRow = row("Sets:");
        setsField = new JTextField();
        setsField.setMaximumSize(new Dimension(120, 30));
        setsRow.add(setsField);

        JPanel valueRow = row("Value:");
        valueField = new JTextField();
        valueField.setMaximumSize(new Dimension(120, 30));
        leftValueField = new JTextField();
        leftValueField.setMaximumSize(new Dimension(80, 30));
        rightValueField = new JTextField();
        rightValueField.setMaximumSize(new Dimension(80, 30));
        valueRow.add(valueField);
        valueRow.add(new JLabel("L:"));
        valueRow.add(leftValueField);
        valueRow.add(new JLabel("R:"));
        valueRow.add(rightValueField);

        JPanel rpeRow = row("RPE:");
        rpeField = new JTextField();
        rpeField.setMaximumSize(new Dimension(120, 30));
        JButton addSetButton = new JButton("Add Set");
        JButton removeSetButton = new JButton("Remove Selected");
        rpeRow.add(rpeField);
        rpeRow.add(Box.createHorizontalStrut(8));
        rpeRow.add(addSetButton);
        rpeRow.add(Box.createHorizontalStrut(8));
        rpeRow.add(removeSetButton);

        detailedSetsModel = new DefaultListModel<>();
        detailedSets = new ArrayList<>();
        JList<String> detailedSetList = new JList<>(detailedSetsModel);
        JScrollPane setScroll = new JScrollPane(detailedSetList);
        setScroll.setPreferredSize(new Dimension(700, 110));

        JPanel flagsRow = new JPanel();
        flagsRow.setLayout(new BoxLayout(flagsRow, BoxLayout.X_AXIS));
        warmupCheck = new JCheckBox("Warm-up");
        deloadCheck = new JCheckBox("Deload");
        flagsRow.add(warmupCheck);
        flagsRow.add(Box.createHorizontalStrut(16));
        flagsRow.add(deloadCheck);

        JPanel dateRow = row("Date (YYYY-MM-DD):");
        dateField = new JTextField(LocalDate.now().toString());
        dateField.setMaximumSize(new Dimension(160, 30));
        dateRow.add(dateField);

        JPanel notesRow = row("Notes:");
        notesField = new JTextField();
        notesField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        notesRow.add(notesField);

        JPanel actionRow = new JPanel();
        actionRow.setLayout(new BoxLayout(actionRow, BoxLayout.X_AXIS));
        JButton addExecutionButton = new JButton("Add Execution");
        actionRow.add(addExecutionButton);

        statusLabel = new JLabel(" ");

        newLiftPanel = buildNewLiftPanel();
        newLiftPanel.setVisible(false);

        root.add(liftRow);
        root.add(Box.createVerticalStrut(8));
        root.add(weightRow);
        root.add(Box.createVerticalStrut(8));
        root.add(metricRow);
        root.add(Box.createVerticalStrut(8));
        root.add(setModeRow);
        root.add(Box.createVerticalStrut(8));
        root.add(setsRow);
        root.add(Box.createVerticalStrut(8));
        root.add(valueRow);
        root.add(Box.createVerticalStrut(8));
        root.add(rpeRow);
        root.add(Box.createVerticalStrut(8));
        root.add(new JLabel("Detailed Sets:"));
        root.add(setScroll);
        root.add(Box.createVerticalStrut(8));
        root.add(flagsRow);
        root.add(Box.createVerticalStrut(8));
        root.add(dateRow);
        root.add(Box.createVerticalStrut(8));
        root.add(notesRow);
        root.add(Box.createVerticalStrut(8));
        root.add(actionRow);
        root.add(Box.createVerticalStrut(8));
        root.add(statusLabel);
        root.add(Box.createVerticalStrut(16));
        root.add(newLiftPanel);

        add(new JScrollPane(root), BorderLayout.CENTER);

        refreshButton.addActionListener(e -> reloadLifts(true));
        newLiftButton.addActionListener(e -> newLiftPanel.setVisible(!newLiftPanel.isVisible()));
        loadLastButton.addActionListener(e -> loadLastExecution());
        addSetButton.addActionListener(e -> addDetailedSet());
        removeSetButton.addActionListener(e -> {
            int index = detailedSetList.getSelectedIndex();
            if (index >= 0) {
                detailedSets.remove(index);
                detailedSetsModel.remove(index);
            }
        });
        addExecutionButton.addActionListener(e -> addExecution());
        setModeCombo.addActionListener(e -> updateSetModeVisibility());
        metricModeCombo.addActionListener(e -> updateMetricInputVisibility());

        updateSetModeVisibility();
        updateMetricInputVisibility();
        reloadLifts(true);
    }

    private JPanel buildNewLiftPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder("Create New Lift"));

        JPanel nameRow = row("Name:");
        newLiftName = new JTextField();
        newLiftName.setMaximumSize(new Dimension(240, 30));
        nameRow.add(newLiftName);

        JPanel regionRow = row("Region:");
        newLiftRegion = new JComboBox<>(LiftRegion.values());
        newLiftRegion.setMaximumSize(new Dimension(150, 30));
        regionRow.add(newLiftRegion);

        JPanel typeRow = row("Type:");
        newLiftType = new JComboBox<>(LiftType.values());
        newLiftType.setMaximumSize(new Dimension(220, 30));
        typeRow.add(newLiftType);

        JPanel musclesRow = row("Muscles:");
        DefaultListModel<Muscle> musclesModel = new DefaultListModel<>();
        List<Muscle> sortedMuscles = new ArrayList<>(List.of(Muscle.values()));
        sortedMuscles.sort(Comparator.comparing(this::toDisplayLabel));
        for (Muscle muscle : sortedMuscles) {
            musclesModel.addElement(muscle);
        }
        newLiftMuscles = new JList<>(musclesModel);
        newLiftMuscles.setVisibleRowCount(4);
        JScrollPane muscleScroll = new JScrollPane(newLiftMuscles);
        muscleScroll.setPreferredSize(new Dimension(260, 90));
        musclesRow.add(muscleScroll);

        JPanel notesRow = row("Notes:");
        newLiftNotes = new JTextField();
        newLiftNotes.setMaximumSize(new Dimension(320, 30));
        notesRow.add(newLiftNotes);

        JPanel actions = new JPanel();
        actions.setLayout(new BoxLayout(actions, BoxLayout.X_AXIS));
        JButton createButton = new JButton("Create");
        JButton cancelButton = new JButton("Cancel");
        actions.add(createButton);
        actions.add(Box.createHorizontalStrut(8));
        actions.add(cancelButton);

        createButton.addActionListener(e -> createLift());
        cancelButton.addActionListener(e -> {
            newLiftPanel.setVisible(false);
            clearNewLiftForm();
        });

        panel.add(nameRow);
        panel.add(regionRow);
        panel.add(typeRow);
        panel.add(musclesRow);
        panel.add(notesRow);
        panel.add(actions);
        return panel;
    }

    private JPanel row(String label) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        panel.add(new JLabel(label));
        panel.add(Box.createHorizontalStrut(8));
        return panel;
    }

    private void reloadLifts(boolean keepSelection) {
        String selected = keepSelection ? (String) liftCombo.getSelectedItem() : null;
        try {
            lifts = database.listLifts();
            DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
            for (Lift lift : lifts) {
                model.addElement(lift.name());
            }
            liftCombo.setModel(model);
            if (selected != null) {
                liftCombo.setSelectedItem(selected);
            }
            if (liftCombo.getSelectedIndex() < 0 && model.getSize() > 0) {
                liftCombo.setSelectedIndex(0);
            }
            setStatus("Lifts loaded", false);
        } catch (Exception e) {
            setStatus("Failed to load lifts: " + e.getMessage(), true);
        }
    }

    private void updateSetModeVisibility() {
        boolean detailed = setModeCombo.getSelectedItem() == SetInputMode.DETAILED;
        setsField.setEnabled(!detailed);
    }

    private void updateMetricInputVisibility() {
        boolean lr = metricModeCombo.getSelectedItem() == MetricInputMode.REPS_LR;
        valueField.setEnabled(!lr);
        leftValueField.setEnabled(lr);
        rightValueField.setEnabled(lr);
    }

    private void addDetailedSet() {
        try {
            ExecutionSet set = buildSingleSet();
            detailedSets.add(set);
            detailedSetsModel.addElement(formatSet(set));
            setStatus("Added set", false);
        } catch (IllegalArgumentException ex) {
            setStatus(ex.getMessage(), true);
        }
    }

    private void addExecution() {
        String liftName = (String) liftCombo.getSelectedItem();
        if (liftName == null || liftName.isBlank()) {
            setStatus("No lift selected", true);
            return;
        }

        try {
            List<ExecutionSet> sets;
            if (setModeCombo.getSelectedItem() == SetInputMode.SIMPLE) {
                int count = Integer.parseInt(setsField.getText().trim());
                if (count <= 0) {
                    throw new IllegalArgumentException("Sets must be > 0");
                }
                ExecutionSet set = buildSingleSet();
                sets = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    sets.add(set);
                }
            } else {
                if (detailedSets.isEmpty()) {
                    throw new IllegalArgumentException("No detailed sets added");
                }
                sets = List.copyOf(detailedSets);
            }

            LocalDate date = LocalDate.parse(dateField.getText().trim());
            LiftExecution execution = new LiftExecution(
                    null,
                    date,
                    sets,
                    warmupCheck.isSelected(),
                    deloadCheck.isSelected(),
                    notesField.getText()
            );
            database.addLiftExecution(liftName, execution);

            detailedSets.clear();
            detailedSetsModel.clear();
            setsField.setText("");
            valueField.setText("");
            leftValueField.setText("");
            rightValueField.setText("");
            rpeField.setText("");
            notesField.setText("");
            setStatus("Execution added", false);
        } catch (DateTimeParseException e) {
            setStatus("Date must be YYYY-MM-DD", true);
        } catch (IllegalArgumentException e) {
            setStatus(e.getMessage(), true);
        } catch (Exception e) {
            setStatus("Failed to add execution: " + e.getMessage(), true);
        }
    }

    private void loadLastExecution() {
        String liftName = (String) liftCombo.getSelectedItem();
        if (liftName == null || liftName.isBlank()) {
            setStatus("No lift selected", true);
            return;
        }

        try {
            List<LiftExecution> executions = database.getExecutions(liftName);
            if (executions.isEmpty()) {
                setStatus("No prior executions for this lift", true);
                return;
            }
            LiftExecution last = executions.get(0);
            dateField.setText(LocalDate.now().toString());
            warmupCheck.setSelected(last.warmup());
            deloadCheck.setSelected(last.deload());
            notesField.setText(last.notes() == null ? "" : last.notes());

            detailedSets.clear();
            detailedSetsModel.clear();
            detailedSets.addAll(last.sets());
            for (ExecutionSet set : last.sets()) {
                detailedSetsModel.addElement(formatSet(set));
            }
            setModeCombo.setSelectedItem(SetInputMode.DETAILED);

            if (!last.sets().isEmpty()) {
                ExecutionSet first = last.sets().get(0);
                weightField.setText(first.weight());
                rpeField.setText(first.rpe() == null ? "" : String.format(Locale.ROOT, "%s", first.rpe()));
                if (first.metric() instanceof SetMetric.Reps reps) {
                    metricModeCombo.setSelectedItem(MetricInputMode.REPS);
                    valueField.setText(String.valueOf(reps.reps()));
                } else if (first.metric() instanceof SetMetric.RepsLr repsLr) {
                    metricModeCombo.setSelectedItem(MetricInputMode.REPS_LR);
                    leftValueField.setText(String.valueOf(repsLr.left()));
                    rightValueField.setText(String.valueOf(repsLr.right()));
                } else if (first.metric() instanceof SetMetric.TimeSecs timeSecs) {
                    metricModeCombo.setSelectedItem(MetricInputMode.TIME_SECS);
                    valueField.setText(String.valueOf(timeSecs.seconds()));
                } else if (first.metric() instanceof SetMetric.DistanceFeet distanceFeet) {
                    metricModeCombo.setSelectedItem(MetricInputMode.DISTANCE_FEET);
                    valueField.setText(String.valueOf(distanceFeet.feet()));
                }
            }
            setStatus("Loaded last execution", false);
        } catch (Exception e) {
            setStatus("Failed to load execution: " + e.getMessage(), true);
        }
    }

    private ExecutionSet buildSingleSet() {
        String weight = weightField.getText().trim();
        if (weight.isBlank()) {
            throw new IllegalArgumentException("Weight is required");
        }

        Float rpe = null;
        String rpeText = rpeField.getText().trim();
        if (!rpeText.isBlank()) {
            rpe = Float.parseFloat(rpeText);
        }

        MetricInputMode metricMode = (MetricInputMode) metricModeCombo.getSelectedItem();
        if (metricMode == null) {
            throw new IllegalArgumentException("Metric mode is required");
        }

        SetMetric metric = switch (metricMode) {
            case REPS -> new SetMetric.Reps(parseInt(valueField, "Value"));
            case REPS_LR -> new SetMetric.RepsLr(parseInt(leftValueField, "Left value"), parseInt(rightValueField, "Right value"));
            case TIME_SECS -> new SetMetric.TimeSecs(parseInt(valueField, "Seconds"));
            case DISTANCE_FEET -> new SetMetric.DistanceFeet(parseInt(valueField, "Feet"));
        };

        return new ExecutionSet(metric, weight, rpe);
    }

    private int parseInt(JTextField field, String label) {
        String text = field.getText().trim();
        if (text.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return Integer.parseInt(text);
    }

    private void createLift() {
        String name = newLiftName.getText().trim();
        if (name.isBlank()) {
            setStatus("Lift name required", true);
            return;
        }

        try {
            database.addLift(
                    name,
                    (LiftRegion) newLiftRegion.getSelectedItem(),
                    (LiftType) newLiftType.getSelectedItem(),
                    newLiftMuscles.getSelectedValuesList(),
                    newLiftNotes.getText()
            );
            clearNewLiftForm();
            newLiftPanel.setVisible(false);
            reloadLifts(false);
            liftCombo.setSelectedItem(name);
            setStatus("Lift created", false);
        } catch (Exception e) {
            setStatus("Failed to create lift: " + e.getMessage(), true);
        }
    }

    private void clearNewLiftForm() {
        newLiftName.setText("");
        newLiftRegion.setSelectedItem(LiftRegion.UPPER);
        newLiftType.setSelectedItem(LiftType.BENCH_PRESS);
        newLiftMuscles.clearSelection();
        newLiftNotes.setText("");
    }

    private String formatSet(ExecutionSet set) {
        String metric = formatMetric(set.metric());
        String rpe = set.rpe() == null ? "" : " RPE " + set.rpe();
        return metric + " @ " + set.weight() + rpe;
    }

    private String formatMetric(SetMetric metric) {
        if (metric instanceof SetMetric.Reps reps) {
            return reps.reps() + " reps";
        }
        if (metric instanceof SetMetric.RepsLr repsLr) {
            return repsLr.left() + "L/" + repsLr.right() + "R reps";
        }
        if (metric instanceof SetMetric.TimeSecs timeSecs) {
            return timeSecs.seconds() + " sec";
        }
        if (metric instanceof SetMetric.DistanceFeet distanceFeet) {
            return distanceFeet.feet() + " ft";
        }
        if (metric instanceof SetMetric.RepsRange range) {
            return range.min() + "-" + range.max() + " reps";
        }
        return "unknown";
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

    private void setStatus(String message, boolean error) {
        statusLabel.setText(message);
        statusLabel.setForeground(error ? java.awt.Color.RED : java.awt.Color.GRAY);
    }

    private enum MetricInputMode {
        REPS("Reps"),
        REPS_LR("L/R Reps"),
        TIME_SECS("Seconds"),
        DISTANCE_FEET("Feet");

        private final String label;

        MetricInputMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private enum SetInputMode {
        SIMPLE("Sets x Value"),
        DETAILED("Individual Sets");

        private final String label;

        SetInputMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
