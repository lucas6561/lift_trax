package com.lifttrax.db;

import java.util.List;

/** The bounded data needed for the initial dashboard; a suggestion may have no execution yet. */
public record DashboardSnapshot(
    boolean hasLifts,
    int todayCount,
    List<LiftExecutionRow> suggestions,
    List<LiftExecutionRow> recentExecutions) {}
