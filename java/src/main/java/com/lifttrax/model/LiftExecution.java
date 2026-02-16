package com.lifttrax.model;

import java.time.LocalDate;

public record LiftExecution(
    LocalDate date,
    int sets,
    int reps,
    double weight,
    Double rpe,
    String notes
) {
}
