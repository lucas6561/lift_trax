package com.lifttrax.ui;

import com.lifttrax.db.SqliteRepository;
import com.lifttrax.model.Lift;
import com.lifttrax.model.LiftExecution;
import com.lifttrax.model.LiftType;
import com.lifttrax.model.Muscle;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
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
import java.util.Set;
import java.util.stream.Collectors;

public class LiftTraxApp extends Application {
    private final SqliteRepository repository = new SqliteRepository("lifts.db");
    private final ListView<String> liftListView = new ListView<>();

    private final TextField filterExerciseField = new TextField();
    private final ComboBox<Muscle> filterMuscleBox = new ComboBox<>();

    @Override
    public void start(Stage stage) {
        repository.initialize();

        TabPane tabPane = new TabPane();
        Tab addTab = new Tab("Add Execution", buildAddExecutionPane());
        addTab.setClosable(false);

        Tab listTab = new Tab("Lift History", buildListPane());
        listTab.setClosable(false);

        tabPane.getTabs().addAll(addTab, listTab);

        Scene scene = new Scene(tabPane, 920, 640);
        stage.setTitle("Lift Trax (JavaFX)");
        stage.setScene(scene);
        stage.show();

        refreshList();
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
        ComboBox<LiftType> liftTypeBox = new ComboBox<>(FXCollections.observableArrayList(LiftType.values()));
        ListView<Muscle> muscleList = new ListView<>(FXCollections.observableArrayList(Muscle.values()));
        muscleList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        muscleList.setPrefHeight(120);
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

        Button submit = new Button("Save Execution");
        submit.setOnAction(event -> {
            try {
                String exercise = exerciseField.getText().trim();
                if (exercise.isEmpty()) {
                    throw new IllegalArgumentException("Exercise is required");
                }

                LiftExecution execution = new LiftExecution(
                    datePicker.getValue(),
                    Integer.parseInt(setsField.getText().trim()),
                    Integer.parseInt(repsField.getText().trim()),
                    Double.parseDouble(weightField.getText().trim()),
                    rpeField.getText().isBlank() ? null : Double.parseDouble(rpeField.getText().trim()),
                    notesArea.getText().trim()
                );

                Set<Muscle> muscles = EnumSet.copyOf(muscleList.getSelectionModel().getSelectedItems());
                LiftType type = liftTypeBox.getValue();

                repository.addExecution(exercise, type, muscles, execution);
                showInfo("Saved", "Lift execution recorded.");
                refreshList();
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
        filterMuscleBox.setPromptText("Muscle");
        filterExerciseField.setPromptText("Exercise name");

        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> refreshList());

        HBox filters = new HBox(10, new Label("Filter:"), filterExerciseField, filterMuscleBox, refresh);
        filters.setPadding(new Insets(16));

        BorderPane pane = new BorderPane();
        pane.setTop(filters);
        pane.setCenter(liftListView);
        BorderPane.setMargin(liftListView, new Insets(0, 16, 16, 16));
        return pane;
    }

    private void refreshList() {
        String exerciseFilter = filterExerciseField.getText();
        Muscle muscle = filterMuscleBox.getValue();
        Set<Muscle> muscles = muscle == null ? EnumSet.noneOf(Muscle.class) : EnumSet.of(muscle);

        liftListView.getItems().setAll(
            repository.listLifts(exerciseFilter, muscles)
                .stream()
                .map(this::formatLift)
                .toList()
        );
    }

    private String formatLift(Lift lift) {
        String muscles = lift.muscles().isEmpty()
            ? ""
            : " [" + lift.muscles().stream().map(Enum::name).collect(Collectors.joining(", ")) + "]";

        String header = lift.name() + " (" + lift.region() + ")"
            + (lift.mainType() == null ? "" : " [" + lift.mainType() + "]")
            + muscles;

        String history = lift.recentExecutions().isEmpty()
            ? "  - no records"
            : "  - " + lift.recentExecutions().stream().map(this::formatExecution).collect(Collectors.joining(" | "));

        return header + System.lineSeparator() + history;
    }

    private String formatExecution(LiftExecution execution) {
        String rpe = execution.rpe() == null ? "" : " RPE " + execution.rpe();
        String notes = execution.notes() == null || execution.notes().isBlank() ? "" : " (" + execution.notes() + ")";
        return "%s: %dx %d reps @ %.1f lb%s%s".formatted(
            execution.date(),
            execution.sets(),
            execution.reps(),
            execution.weight(),
            rpe,
            notes
        );
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
        alert.setHeaderText("Could not save lift");
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
