package com.lifttrax.ui;

import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftRegion;
import com.lifttrax.models.LiftType;
import com.lifttrax.models.Muscle;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class LiftFilterPanel extends JPanel {
    private final JTextField nameFilter;
    private final JComboBox<FilterOption<LiftRegion>> regionFilter;
    private final JComboBox<FilterOption<LiftType>> typeFilter;
    private final JList<Muscle> muscleFilter;

    LiftFilterPanel(Runnable onChange) {
        super(new FlowLayout(FlowLayout.LEFT, 6, 4));
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        nameFilter = new JTextField();
        nameFilter.setPreferredSize(new Dimension(190, 26));

        regionFilter = new JComboBox<>(buildRegionOptions());
        regionFilter.setPreferredSize(new Dimension(95, 26));

        typeFilter = new JComboBox<>(buildTypeOptions());
        typeFilter.setPreferredSize(new Dimension(150, 26));

        DefaultListModel<Muscle> muscleModel = new DefaultListModel<>();
        Arrays.stream(Muscle.values())
                .sorted(Comparator.comparing(LiftFilterPanel::toDisplayLabel))
                .forEach(muscleModel::addElement);
        muscleFilter = new JList<>(muscleModel);
        muscleFilter.setVisibleRowCount(3);
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

        JScrollPane muscleScroll = new JScrollPane(muscleFilter);
        muscleScroll.setPreferredSize(new Dimension(190, 66));

        JButton clearFilters = new JButton("Clear");
        clearFilters.addActionListener(event -> {
            nameFilter.setText("");
            regionFilter.setSelectedIndex(0);
            typeFilter.setSelectedIndex(0);
            muscleFilter.clearSelection();
            onChange.run();
        });

        nameFilter.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                onChange.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                onChange.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                onChange.run();
            }
        });
        regionFilter.addItemListener(event -> onChange.run());
        typeFilter.addItemListener(event -> onChange.run());
        muscleFilter.addListSelectionListener(event -> {
            if (!muscleFilter.getValueIsAdjusting()) {
                onChange.run();
            }
        });

        add(new JLabel("Name:"));
        add(nameFilter);
        add(new JLabel("Region:"));
        add(regionFilter);
        add(new JLabel("Type:"));
        add(typeFilter);
        add(new JLabel("Muscles:"));
        add(muscleScroll);
        add(clearFilters);
    }

    List<Lift> apply(List<Lift> lifts) {
        List<Muscle> selectedMuscles = muscleFilter.getSelectedValuesList();
        String name = nameFilter.getText() == null ? "" : nameFilter.getText().trim().toLowerCase(Locale.ROOT);
        FilterOption<LiftRegion> selectedRegion = (FilterOption<LiftRegion>) regionFilter.getSelectedItem();
        FilterOption<LiftType> selectedType = (FilterOption<LiftType>) typeFilter.getSelectedItem();

        return lifts.stream()
                .filter(lift -> name.isBlank() || lift.name().toLowerCase(Locale.ROOT).contains(name))
                .filter(lift -> selectedRegion == null || selectedRegion.value() == null || lift.region() == selectedRegion.value())
                .filter(lift -> selectedType == null || selectedType.value() == null || lift.main() == selectedType.value())
                .filter(lift -> selectedMuscles.isEmpty() || selectedMuscles.stream().allMatch(lift.muscles()::contains))
                .toList();
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
                .sorted(Comparator.comparing(LiftFilterPanel::toDisplayLabel))
                .forEach(type -> options.add(new FilterOption<>(toDisplayLabel(type), type)));
        return options.toArray(new FilterOption[0]);
    }

    static String toDisplayLabel(Enum<?> value) {
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

    private record FilterOption<T>(String label, T value) {
        @Override
        public String toString() {
            return label;
        }
    }
}
