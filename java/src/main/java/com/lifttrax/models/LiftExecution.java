package com.lifttrax.models;

import java.time.LocalDate;
import java.util.List;

public record LiftExecution(
        Integer id,
        LocalDate date,
        List<ExecutionSet> sets,
        boolean warmup,
        boolean deload,
        String notes
) {}
