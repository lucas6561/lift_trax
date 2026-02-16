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
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class AddExecutionTabPanel extends JPanel {
    private final Database database;
    private final LiftFilterPanel filterPanel;

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
    private final JTextField newLiftName;
    private final JComboBox<LiftRegion> newLiftRegion;
    private final JComboBox<LiftType> newLiftType;
    private final JList<Muscle> newLiftMuscles;
    private final JTextField newLiftNotes;

    private List<Lift> allLifts = new ArrayList<>();

    AddExecutionTabPanel(Database database) {
        super(new BorderLayout(0, 4));
        this.database = database;

        filterPanel = new LiftFilterPanel(() -> refreshLiftOptions(true));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(6, 8, 8, 8));

        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createTitledBorder("Add Lift Execution"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 4, 2, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        liftCombo = new JComboBox<>();
        liftCombo.setPreferredSize(new Dimension(250, 26));
        JButton refreshButton = new JButton("Refresh");
        JButton loadLastButton = new JButton("Load Last");
        JButton newLiftButton = new JButton("New Lift");

        int row = 0;
        addRowLabel(formPanel, gbc, row, "Lift:");
        JPanel liftRow = inlinePanel();
        liftRow.add(liftCombo);
        liftRow.add(refreshButton);
        liftRow.add(loadLastButton);
        liftRow.add(newLiftButton);
        addRowValue(formPanel, gbc, row++, liftRow);

        addRowLabel(formPanel, gbc, row, "Weight:");
        weightField = sizedField(180);
        addRowValue(formPanel, gbc, row++, weightField);

        addRowLabel(formPanel, gbc, row, "Metric:");
        metricModeCombo = new JComboBox<>(MetricInputMode.values());
        metricModeCombo.setPreferredSize(new Dimension(160, 26));
        addRowValue(formPanel, gbc, row++, metricModeCombo);

        addRowLabel(formPanel, gbc, row, "Set Input:");
        setModeCombo = new JComboBox<>(SetInputMode.values());
        setModeCombo.setPreferredSize(new Dimension(160, 26));
        addRowValue(formPanel, gbc, row++, setModeCombo);

        addRowLabel(formPanel, gbc, row, "Sets:");
        setsField = sizedField(90);
        addRowValue(formPanel, gbc, row++, setsField);

        addRowLabel(formPanel, gbc, row, "Value:");
        valueField = sizedField(90);
        leftValueField = sizedField(70);
        rightValueField = sizedField(70);
        JPanel valueRow = inlinePanel();
        valueRow.add(valueField);
        valueRow.add(new JLabel("L:"));
        valueRow.add(leftValueField);
        valueRow.add(new JLabel("R:"));
        valueRow.add(rightValueField);
        addRowValue(formPanel, gbc, row++, valueRow);

        addRowLabel(formPanel, gbc, row, "RPE:");
        rpeField = sizedField(90);
        JButton addSetButton = new JButton("Add Set");
        JButton removeSetButton = new JButton("Remove Set");
        JPanel rpeRow = inlinePanel();
        rpeRow.add(rpeField);
        rpeRow.add(addSetButton);
        rpeRow.add(removeSetButton);
        addRowValue(formPanel, gbc, row++, rpeRow);

        addRowLabel(formPanel, gbc, row, "Detailed Sets:");
        detailedSetsModel = new DefaultListModel<>();
        detailedSets = new ArrayList<>();
        JList<String> detailedSetList = new JList<>(detailedSetsModel);
        JScrollPane setScroll = new JScrollPane(detailedSetList);
        setScroll.setPreferredSize(new Dimension(460, 95));
        addRowValue(formPanel, gbc, row++, setScroll);

        addRowLabel(formPanel, gbc, row, "Flags:");
        warmupCheck = new JCheckBox("Warm-up");
        deloadCheck = new JCheckBox("Deload");
        JPanel flags = inlinePanel();
        flags.add(warmupCheck);
        flags.add(deloadCheck);
        addRowValue(formPanel, gbc, row++, flags);

        addRowLabel(formPanel, gbc, row, "Date:");
        dateField = sizedField(120);
        dateField.setText(LocalDate.now().toString());
        addRowValue(formPanel, gbc, row++, dateField);

        addRowLabel(formPanel, gbc, row, "Notes:");
        notesField = sizedField(460);
        addRowValue(formPanel, gbc, row++, notesField);

        JButton addExecutionButton = new JButton("Add Execution");
        addRowLabel(formPanel, gbc, row, "");
        addRowValue(formPanel, gbc, row++, addExecutionButton);

        statusLabel = new JLabel(" ");
        statusLabel.setForeground(new Color(160, 160, 160));

        newLiftPanel = new JPanel();
        newLiftPanel.setLayout(new BoxLayout(newLiftPanel, BoxLayout.Y_AXIS));
        newLiftPanel.setBorder(BorderFactory.createTitledBorder("Create New Lift"));
        newLiftPanel.setVisible(false);

        newLiftName = sizedField(220);
        newLiftRegion = new JComboBox<>(LiftRegion.values());
        newLiftType = new JComboBox<>(LiftType.values());
        newLiftMuscles = buildMuscleList();
        newLiftNotes = sizedField(320);

        newLiftPanel.add(labeledRow("Name:", newLiftName));
        newLiftPanel.add(labeledRow("Region:", newLiftRegion));
        newLiftPanel.add(labeledRow("Type:", newLiftType));
        JScrollPane muscleScroll = new JScrollPane(newLiftMuscles);
        muscleScroll.setPreferredSize(new Dimension(260, 82));
        newLiftPanel.add(labeledRow("Muscles:", muscleScroll));
        newLiftPanel.add(labeledRow("Notes:", newLiftNotes));

        JPanel newLiftActions = inlinePanel();
        JButton createLiftButton = new JButton("Create");
        JButton cancelLiftButton = new JButton("Cancel");
        newLiftActions.add(createLiftButton);
        newLiftActions.add(cancelLiftButton);
        newLiftPanel.add(newLiftActions);

        content.add(formPanel);
        content.add(Box.createVerticalStrut(4));
        content.add(statusLabel);
        content.add(Box.createVerticalStrut(6));
        content.add(newLiftPanel);

        add(filterPanel, BorderLayout.NORTH);
        add(new JScrollPane(content), BorderLayout.CENTER);

        refreshButton.addActionListener(event -> reloadLifts(true));
        newLiftButton.addActionListener(event -> newLiftPanel.setVisible(!newLiftPanel.isVisible()));
        loadLastButton.addActionListener(event -> loadLastExecution());
        addSetButton.addActionListener(event -> addDetailedSet());
        removeSetButton.addActionListener(event -> {
            int index = detailedSetList.getSelectedIndex();
            if (index >= 0) {
                detailedSets.remove(index);
                detailedSetsModel.remove(index);
            }
        });
        addExecutionButton.addActionListener(event -> addExecution());
        setModeCombo.addActionListener(event -> updateSetModeVisibility());
        metricModeCombo.addActionListener(event -> updateMetricInputVisibility());
        createLiftButton.addActionListener(event -> createLift());
        cancelLiftButton.addActionListener(event -> {
            newLiftPanel.setVisible(false);
            clearNewLiftForm();
        });

        updateSetModeVisibility();
        updateMetricInputVisibility();
        reloadLifts(true);
    }

    private void addRowLabel(JPanel panel, GridBagConstraints gbc, int row, String label) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        panel.add(new JLabel(label), gbc);
    }

    private void addRowValue(JPanel panel, GridBagConstraints gbc, int row, java.awt.Component component) {
        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.weightx = 1;
        panel.add(component, gbc);
    }

    private JPanel inlinePanel() {
        return new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    }

    private JPanel labeledRow(String label, java.awt.Component component) {
        JPanel panel = inlinePanel();
        panel.add(new JLabel(label));
        panel.add(component);
        return panel;
    }

    private JTextField sizedField(int width) {
        JTextField field = new JTextField();
        field.setPreferredSize(new Dimension(width, 26));
        return field;
    }

    private JList<Muscle> buildMuscleList() {
        DefaultListModel<Muscle> musclesModel = new DefaultListModel<>();
        List<Muscle> sortedMuscles = new ArrayList<>(List.of(Muscle.values()));
        sortedMuscles.sort(Comparator.comparing(LiftFilterPanel::toDisplayLabel));
        for (Muscle muscle : sortedMuscles) {
            musclesModel.addElement(muscle);
        }
        return new JList<>(musclesModel);
    }

    private void reloadLifts(boolean keepSelection) {
        try {
            allLifts = database.listLifts();
            refreshLiftOptions(keepSelection);
            setStatus("Lifts loaded", false);
        } catch (Exception e) {
            setStatus("Failed to load lifts: " + e.getMessage(), true);
        }
    }

    private void refreshLiftOptions(boolean keepSelection) {
        String selected = keepSelection ? (String) liftCombo.getSelectedItem() : null;
        List<Lift> filtered = filterPanel.apply(allLifts);

        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        for (Lift lift : filtered) {
            model.addElement(lift.name());
        }
        liftCombo.setModel(model);

        if (selected != null) {
            liftCombo.setSelectedItem(selected);
        }
        if (liftCombo.getSelectedIndex() < 0 && model.getSize() > 0) {
            liftCombo.setSelectedIndex(0);
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
        } catch (RuntimeException ex) {
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
        } catch (RuntimeException e) {
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

    private void setStatus(String message, boolean error) {
        statusLabel.setText(message);
        statusLabel.setForeground(error ? new Color(220, 110, 110) : new Color(160, 160, 160));
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
