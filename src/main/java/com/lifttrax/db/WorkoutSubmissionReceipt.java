package com.lifttrax.db;

/** Durable result of one idempotent planned-workout submission. */
public record WorkoutSubmissionReceipt(
    String fingerprint, int loggedExecutionCount, int skippedExercises, int skippedSets) {}
