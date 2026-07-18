package com.lifttrax.db;

import com.lifttrax.models.LiftExecution;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Database operations needed by the web training surfaces. */
public interface TrainingDataStore extends Database {
  List<LiftExecutionRow> getExecutionsBetween(LocalDate start, LocalDate end) throws Exception;

  ExecutionHistorySummary executionHistorySummary(LocalDate start, LocalDate end) throws Exception;

  LiftExecution getLastExecution(String liftName, boolean warmup, boolean deload) throws Exception;

  LiftExecution getExecution(String liftName, int executionId) throws Exception;

  Map<String, LiftExecution> latestExecutionsByLift() throws Exception;

  Map<String, Boolean> liftEnabledStatuses() throws Exception;
}
