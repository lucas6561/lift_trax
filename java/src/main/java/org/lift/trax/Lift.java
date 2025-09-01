package org.lift.trax;

import java.util.ArrayList;
import java.util.List;

public class Lift {
    public String name;
    public LiftRegion region;
    public LiftType main;
    public List<Muscle> muscles = new ArrayList<>();
    public String notes = "";
    public List<LiftExecution> executions = new ArrayList<>();

    public Lift() {}

    public Lift(String name, LiftRegion region, LiftType main, List<Muscle> muscles, String notes) {
        this.name = name;
        this.region = region;
        this.main = main;
        if (muscles != null) {
            this.muscles = muscles;
        }
        if (notes != null) {
            this.notes = notes;
        }
    }

    @Override
    public String toString() {
        return name + " (" + region + ")" + (main != null ? " - " + main : "") + (notes.isEmpty() ? "" : ": " + notes);
    }
}
