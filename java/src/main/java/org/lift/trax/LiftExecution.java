package org.lift.trax;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class LiftExecution {
    public Integer id;
    public LocalDate date;
    public List<ExecutionSet> sets = new ArrayList<>();
    public boolean warmup;
    public String notes = "";

    public LiftExecution() {}

    public LiftExecution(Integer id, LocalDate date, List<ExecutionSet> sets, boolean warmup, String notes) {
        this.id = id;
        this.date = date;
        if (sets != null) {
            this.sets = sets;
        }
        this.warmup = warmup;
        if (notes != null) {
            this.notes = notes;
        }
    }

    @Override
    public String toString() {
        return date + ": " + sets.size() + " sets" + (warmup ? " (warm-up)" : "") + (notes.isEmpty() ? "" : " - " + notes);
    }
}
