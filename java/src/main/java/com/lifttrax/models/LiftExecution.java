package com.lifttrax.models;

import java.time.LocalDate;
import java.util.List;

/**
 * Simple data holder for LiftExecution values used by LiftTrax.
 */

public record LiftExecution(
        Integer id,
        LocalDate date,
        List<ExecutionSet> sets,
        boolean warmup,
        boolean deload,
        String notes
) {}
