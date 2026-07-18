package com.lifttrax.db;

import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftExecution;

/** A lift paired with one execution for history and dashboard rendering. */
public record LiftExecutionRow(Lift lift, LiftExecution execution) {}
