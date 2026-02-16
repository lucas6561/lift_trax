package com.lifttrax.model;

import java.time.LocalDate;

public record LiftExecution(
    Long id,
    LocalDate date,
    int sets,
    int reps,
    double weight,
    Double rpe,
    boolean warmup,
    boolean deload,
    String notes
) {
}
