package com.lifttrax.ui;

import com.lifttrax.db.Database;
import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftExecution;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerDateModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class LastWeekTabPanel extends JPanel {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("EEE MMM d");

    private final Database database;
    private final LiftFilterPanel filterPanel;
    private final JPanel content;
    private final JSpinner startSpinner;
    private final JSpinner endSpinner;

    private List<Lift> allLifts = new ArrayList<>();

    LastWeekTabPanel(Database database) {
        super(new BorderLayout());
        this.database = database;

        filterPanel = new LiftFilterPanel(this::refreshView);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        JButton prevWeek = new JButton("← Previous Week");
        JButton nextWeek = new JButton("Next Week →");
        JButton currentWeek = new JButton("Current Week");
        JButton lastWeek = new JButton("Last Week");

        startSpinner = new JSpinner(new SpinnerDateModel());
        startSpinner.setEditor(new JSpinner.DateEditor(startSpinner, "yyyy-MM-dd"));
        endSpinner = new JSpinner(new SpinnerDateModel());
        endSpinner.setEditor(new JSpinner.DateEditor(endSpinner, "yyyy-MM-dd"));

        prevWeek.addActionListener(event -> {
            shiftRange(-7);
            refreshView();
        });
        nextWeek.addActionListener(event -> {
            shiftRange(7);
            refreshView();
        });
        currentWeek.addActionListener(event -> {
            setCurrentWeek();
            refreshView();
        });
        lastWeek.addActionListener(event -> {
            setPreviousWeek();
            refreshView();
        });
        startSpinner.addChangeListener(event -> refreshView());
        endSpinner.addChangeListener(event -> refreshView());

        controls.add(prevWeek);
        controls.add(nextWeek);
        controls.add(currentWeek);
        controls.add(lastWeek);
        controls.add(new JLabel("Start:"));
        controls.add(startSpinner);
        controls.add(new JLabel("End:"));
        controls.add(endSpinner);

        content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel top = new JPanel(new BorderLayout());
        top.add(filterPanel, BorderLayout.NORTH);
        top.add(controls, BorderLayout.SOUTH);

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(content), BorderLayout.CENTER);

        setCurrentWeek();
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
        content.removeAll();

        LocalDate start = toLocalDate((Date) startSpinner.getValue());
        LocalDate end = toLocalDate((Date) endSpinner.getValue());
        if (start.isAfter(end)) {
            LocalDate swap = start;
            start = end;
            end = swap;
            startSpinner.setValue(toDate(start));
            endSpinner.setValue(toDate(end));
        }

        List<Lift> filteredLifts = filterPanel.apply(allLifts);
        Map<LocalDate, List<String>> byDate = new LinkedHashMap<>();

        for (Lift lift : filteredLifts) {
            try {
                for (LiftExecution execution : database.getExecutions(lift.name())) {
                    if (execution.date().isBefore(start) || execution.date().isAfter(end)) {
                        continue;
                    }
                    byDate.computeIfAbsent(execution.date(), ignored -> new ArrayList<>())
                            .add(lift.name() + ": " + execution);
                }
            } catch (Exception e) {
                content.add(new JLabel("Failed to load executions for " + lift.name() + ": " + e.getMessage()));
            }
        }

        content.add(new JLabel("Showing " + start.format(DATE_FORMAT) + " through " + end.format(DATE_FORMAT)));

        if (byDate.isEmpty()) {
            content.add(new JLabel("no executions in this 7-day range"));
        } else {
            byDate.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                    .forEach(entry -> {
                        JPanel dayPanel = new JPanel();
                        dayPanel.setLayout(new BoxLayout(dayPanel, BoxLayout.Y_AXIS));
                        dayPanel.setBorder(BorderFactory.createCompoundBorder(
                                BorderFactory.createLineBorder(new java.awt.Color(56, 62, 70)),
                                BorderFactory.createEmptyBorder(6, 6, 6, 6)
                        ));
                        dayPanel.add(new JLabel(entry.getKey().format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))));
                        entry.getValue().forEach(line -> dayPanel.add(new JLabel(line)));
                        content.add(dayPanel);
                    });
        }

        content.revalidate();
        content.repaint();
    }

    private void shiftRange(int days) {
        LocalDate start = toLocalDate((Date) startSpinner.getValue()).plusDays(days);
        LocalDate end = toLocalDate((Date) endSpinner.getValue()).plusDays(days);
        startSpinner.setValue(toDate(start));
        endSpinner.setValue(toDate(end));
    }

    private void setCurrentWeek() {
        LocalDate today = LocalDate.now();
        endSpinner.setValue(toDate(today));
        startSpinner.setValue(toDate(today.minusDays(6)));
    }

    private void setPreviousWeek() {
        LocalDate today = LocalDate.now();
        LocalDate end = today.minusDays(7);
        endSpinner.setValue(toDate(end));
        startSpinner.setValue(toDate(end.minusDays(6)));
    }

    private static LocalDate toLocalDate(Date date) {
        return Instant.ofEpochMilli(date.getTime()).atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private static Date toDate(LocalDate date) {
        return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
}
