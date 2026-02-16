package com.lifttrax.ui;

import com.lifttrax.db.SqliteRepository;
import com.lifttrax.model.Lift;
import com.lifttrax.model.LiftExecution;
import com.lifttrax.model.LiftRegion;
import com.lifttrax.model.LiftType;
import com.lifttrax.model.Muscle;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class LiftTraxApp extends Application {
    private final SqliteRepository repository = new SqliteRepository("lifts.db");

    private final TextField filterExerciseField = new TextField();
    private final ComboBox<Muscle> filterMuscleBox = new ComboBox<>();
    private final ComboBox<LiftRegion> filterRegionBox = new ComboBox<>();
    private final ComboBox<LiftType> filterTypeBox = new ComboBox<>();
    private final ListView<String> liftListView = new ListView<>();

    private final ComboBox<Lift> queryLiftBox = new ComboBox<>();
    private final ListView<String> queryExecutionList = new ListView<>();

    private final DatePicker weekStartPicker = new DatePicker(LocalDate.now().minusDays(6));
    private final DatePicker weekEndPicker = new DatePicker(LocalDate.now());
    private final ListView<String> weekListView = new ListView<>();

    @Override
    public void start(Stage stage) {
        repository.initialize();

        TabPane tabPane = new TabPane();
        tabPane.getTabs().addAll(
            fixedTab("Add", buildAddExecutionPane()),
            fixedTab("Query", buildQueryPane()),
            fixedTab("List", buildListPane()),
            fixedTab("Last Week", buildLastWeekPane())
        );

        Scene scene = new Scene(tabPane, 1080, 700);
        stage.setTitle("Lift Trax");
        stage.setScene(scene);
        stage.show();

        refreshAll();
    }

    private Tab fixedTab(String name, BorderPane pane) {
        Tab tab = new Tab(name, pane);
        tab.setClosable(false);
        return tab;
    }

    private BorderPane buildAddExecutionPane() {
        GridPane form = new GridPane();
        form.setVgap(10);
        form.setHgap(10);
        form.setPadding(new Insets(20));

        TextField exerciseField = new TextField();
        TextField weightField = new TextField();
        TextField repsField = new TextField();
        TextField setsField = new TextField();
        DatePicker datePicker = new DatePicker(LocalDate.now());
        TextField rpeField = new TextField();
        CheckBox warmupBox = new CheckBox("Warm-up");
        CheckBox deloadBox = new CheckBox("Deload");
        ComboBox<LiftType> liftTypeBox = new ComboBox<>(FXCollections.observableArrayList(LiftType.values()));
        ListView<Muscle> muscleList = new ListView<>(FXCollections.observableArrayList(Muscle.values()));
        muscleList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        muscleList.setPrefHeight(140);
        TextArea notesArea = new TextArea();
        notesArea.setPrefRowCount(3);

        form.addRow(0, new Label("Exercise"), exerciseField);
        form.addRow(1, new Label("Weight (lb)"), weightField);
        form.addRow(2, new Label("Reps"), repsField);
        form.addRow(3, new Label("Sets"), setsField);
        form.addRow(4, new Label("Date"), datePicker);
        form.addRow(5, new Label("RPE"), rpeField);
        form.addRow(6, new Label("Lift Type"), liftTypeBox);
        form.addRow(7, new Label("Muscles"), muscleList);
        form.addRow(8, new Label("Notes"), notesArea);
        form.addRow(9, new Label("Flags"), new HBox(12, warmupBox, deloadBox));

        Button submit = new Button("Save Execution");
        submit.setOnAction(event -> {
            try {
                String exercise = exerciseField.getText().trim();
                if (exercise.isEmpty()) {
                    throw new IllegalArgumentException("Exercise is required");
                }

                LiftExecution execution = new LiftExecution(
                    null,
                    datePicker.getValue(),
                    Integer.parseInt(setsField.getText().trim()),
                    Integer.parseInt(repsField.getText().trim()),
                    Double.parseDouble(weightField.getText().trim()),
                    rpeField.getText().isBlank() ? null : Double.parseDouble(rpeField.getText().trim()),
                    warmupBox.isSelected(),
                    deloadBox.isSelected(),
                    notesArea.getText().trim()
                );

                Set<Muscle> muscles = selectedMuscles(muscleList);
                repository.addExecution(exercise, liftTypeBox.getValue(), muscles, execution);
                showInfo("Saved", "Lift execution recorded.");
                refreshAll();
            } catch (Exception e) {
                showError(e.getMessage());
            }
        });

        BorderPane pane = new BorderPane();
        pane.setCenter(form);
        pane.setBottom(new HBox(10, submit));
        BorderPane.setMargin(pane.getBottom(), new Insets(0, 20, 20, 20));
        return pane;
    }

    private BorderPane buildListPane() {
        filterMuscleBox.setItems(FXCollections.observableArrayList(Muscle.values()));
        filterRegionBox.setItems(FXCollections.observableArrayList(LiftRegion.values()));
        filterTypeBox.setItems(FXCollections.observableArrayList(LiftType.values()));
        filterMuscleBox.setPromptText("Muscle");
        filterRegionBox.setPromptText("Region");
        filterTypeBox.setPromptText("Type");
        filterExerciseField.setPromptText("Exercise contains...");

        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> refreshList());

        HBox filters = new HBox(10,
            new Label("Filter:"),
            filterExerciseField,
            filterRegionBox,
            filterTypeBox,
            filterMuscleBox,
            refresh
        );
        filters.setPadding(new Insets(16));

        BorderPane pane = new BorderPane();
        pane.setTop(filters);
        pane.setCenter(liftListView);
        BorderPane.setMargin(liftListView, new Insets(0, 16, 16, 16));
        return pane;
    }

    private BorderPane buildQueryPane() {
        queryLiftBox.setPromptText("Select lift");
        Button load = new Button("Load");
        load.setOnAction(e -> refreshQuery());

        Button editLift = new Button("Edit Lift Notes");
        editLift.setOnAction(e -> {
            Lift lift = queryLiftBox.getValue();
            if (lift == null) {
                return;
            }
            try {
                repository.updateLift(lift.id(), lift.name(), lift.region(), lift.mainType(), Set.copyOf(lift.muscles()), lift.notes() + " [edited]");
                refreshAll();
            } catch (Exception ex) {
                showError(ex.getMessage());
            }
        });

        Button deleteLift = new Button("Delete Lift");
        deleteLift.setOnAction(e -> {
            Lift lift = queryLiftBox.getValue();
            if (lift == null) {
                return;
            }
            repository.deleteLift(lift.id());
            refreshAll();
        });

        Button deleteExecution = new Button("Delete Selected Execution");
        deleteExecution.setOnAction(e -> {
            String selected = queryExecutionList.getSelectionModel().getSelectedItem();
            if (selected == null || !selected.startsWith("#")) {
                return;
            }
            long id = Long.parseLong(selected.substring(1, selected.indexOf(' ')));
            repository.deleteExecution(id);
            refreshQuery();
            refreshList();
            refreshLastWeek();
        });

        HBox top = new HBox(10, queryLiftBox, load, editLift, deleteLift, deleteExecution);
        top.setPadding(new Insets(16));
        queryExecutionList.setPlaceholder(new Label("No executions"));

        BorderPane pane = new BorderPane();
        pane.setTop(top);
        pane.setCenter(queryExecutionList);
        BorderPane.setMargin(queryExecutionList, new Insets(0, 16, 16, 16));
        return pane;
    }

    private BorderPane buildLastWeekPane() {
        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> refreshLastWeek());

        HBox top = new HBox(10,
            new Label("Start"), weekStartPicker,
            new Label("End"), weekEndPicker,
            refresh
        );
        top.setPadding(new Insets(16));

        BorderPane pane = new BorderPane();
        pane.setTop(top);
        pane.setCenter(weekListView);
        BorderPane.setMargin(weekListView, new Insets(0, 16, 16, 16));
        return pane;
    }

    private void refreshAll() {
        refreshList();
        List<Lift> lifts = repository.listLifts(null, null, null, EnumSet.noneOf(Muscle.class));
        queryLiftBox.setItems(FXCollections.observableArrayList(lifts));
        queryLiftBox.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Lift object) {
                return object == null ? "" : object.name();
            }

            @Override
            public Lift fromString(String string) {
                return null;
            }
        });
        refreshQuery();
        refreshLastWeek();
    }

    private void refreshList() {
        Set<Muscle> muscles = filterMuscleBox.getValue() == null
            ? EnumSet.noneOf(Muscle.class)
            : EnumSet.of(filterMuscleBox.getValue());

        liftListView.getItems().setAll(
            repository.listLifts(filterExerciseField.getText(), filterRegionBox.getValue(), filterTypeBox.getValue(), muscles)
                .stream()
                .map(this::formatLift)
                .toList()
        );
    }

    private void refreshQuery() {
        Lift lift = queryLiftBox.getValue();
        if (lift == null) {
            queryExecutionList.getItems().setAll(List.of("Select a lift to inspect and manage all executions."));
            return;
        }
        queryExecutionList.getItems().setAll(
            repository.listExecutions(lift.id()).stream().map(this::formatExecutionForQuery).toList()
        );
    }

    private void refreshLastWeek() {
        LocalDate start = weekStartPicker.getValue();
        LocalDate end = weekEndPicker.getValue();
        weekListView.getItems().setAll(
            repository.listExecutionsForDateRange(start, end)
                .stream()
                .map(this::formatExecutionForQuery)
                .toList()
        );
    }

    private String formatLift(Lift lift) {
        String muscles = lift.muscles().isEmpty() ? "" : " [" + lift.muscles().stream().map(Enum::name).collect(Collectors.joining(", ")) + "]";
        String notes = lift.notes().isBlank() ? "" : " - " + lift.notes();
        String history = lift.recentExecutions().isEmpty()
            ? "  - no records"
            : "  - " + lift.recentExecutions().stream().map(this::formatExecution).collect(Collectors.joining(" | "));
        return "%s (%s)%s%s%n%s".formatted(
            lift.name(),
            lift.region(),
            lift.mainType() == null ? "" : " [" + lift.mainType() + "]",
            muscles + notes,
            history
        );
    }

    private String formatExecution(LiftExecution execution) {
        String flags = (execution.warmup() ? " WARMUP" : "") + (execution.deload() ? " DELOAD" : "");
        String rpe = execution.rpe() == null ? "" : " RPE " + execution.rpe();
        String notes = execution.notes() == null || execution.notes().isBlank() ? "" : " (" + execution.notes() + ")";
        return "%s: %dx %d reps @ %.1f lb%s%s%s".formatted(
            execution.date(), execution.sets(), execution.reps(), execution.weight(), rpe, flags, notes
        );
    }

    private String formatExecutionForQuery(LiftExecution execution) {
        return "#%d %s".formatted(execution.id(), formatExecution(execution));
    }

    private Set<Muscle> selectedMuscles(ListView<Muscle> list) {
        List<Muscle> selected = list.getSelectionModel().getSelectedItems();
        return selected.isEmpty() ? EnumSet.noneOf(Muscle.class) : EnumSet.copyOf(selected);
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText("Action failed");
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
