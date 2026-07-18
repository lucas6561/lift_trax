package com.lifttrax.db;

import java.time.LocalDate;

/** Aggregate execution-history values used by the web dashboard. */
public record ExecutionHistorySummary(
    int count,
    LocalDate minDate,
    LocalDate maxDate,
    LocalDate nearestBefore,
    LocalDate nearestAfter) {}
