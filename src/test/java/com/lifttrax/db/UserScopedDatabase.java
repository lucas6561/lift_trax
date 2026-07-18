package com.lifttrax.db;

import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.LiftRegion;
import com.lifttrax.models.LiftStats;
import com.lifttrax.models.LiftType;
import com.lifttrax.models.Muscle;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Test-only user-scoped facade over the retired SQLite implementation. */
final class UserScopedDatabase implements TrainingDataStore {
  private final SqliteDb db;
  private final String ownerUserId;

  UserScopedDatabase(SqliteDb db, String ownerUserId) {
    this.db = db;
    this.ownerUserId = ownerUserId;
  }

  @Override
  public void addLift(
      String name, LiftRegion region, LiftType main, List<Muscle> muscles, String notes)
      throws Exception {
    db.addLiftForUser(ownerUserId, name, region, main, muscles, notes);
  }

  @Override
  public void addLiftExecution(String name, LiftExecution execution) throws Exception {
    db.addLiftExecutionForUser(ownerUserId, name, execution);
  }

  @Override
  public void updateLift(
      String currentName,
      String newName,
      LiftRegion region,
      LiftType main,
      List<Muscle> muscles,
      String notes)
      throws Exception {
    db.updateLiftForUser(ownerUserId, currentName, newName, region, main, muscles, notes);
  }

  @Override
  public void deleteLift(String name) throws Exception {
    db.deleteLiftForUser(ownerUserId, name);
  }

  @Override
  public void setLiftEnabled(String name, boolean enabled) throws Exception {
    db.setLiftEnabledForUser(ownerUserId, name, enabled);
  }

  @Override
  public boolean isLiftEnabled(String name) throws Exception {
    return db.isLiftEnabledForUser(ownerUserId, name);
  }

  @Override
  public List<LiftExecution> getExecutions(String liftName) throws Exception {
    return db.getExecutionsForUser(ownerUserId, liftName);
  }

  @Override
  public void updateLiftExecution(int execId, LiftExecution execution) throws Exception {
    db.updateLiftExecutionForUser(ownerUserId, execId, execution);
  }

  @Override
  public void deleteLiftExecution(int execId) throws Exception {
    db.deleteLiftExecutionForUser(ownerUserId, execId);
  }

  @Override
  public LiftStats liftStats(String name) throws Exception {
    return db.liftStatsForUser(ownerUserId, name);
  }

  @Override
  public List<Lift> listLifts() throws Exception {
    return db.listLiftsForUser(ownerUserId);
  }

  @Override
  public Lift getLift(String name) throws Exception {
    return db.getLiftForUser(ownerUserId, name);
  }

  @Override
  public List<Lift> liftsByType(LiftType liftType) throws Exception {
    return db.liftsByTypeForUser(ownerUserId, liftType);
  }

  @Override
  public List<Lift> getAccessoriesByMuscle(Muscle muscle) throws Exception {
    return db.getAccessoriesByMuscleForUser(ownerUserId, muscle);
  }

  @Override
  public List<Lift> liftsByRegionAndType(LiftRegion region, LiftType liftType) throws Exception {
    return db.liftsByRegionAndTypeForUser(ownerUserId, region, liftType);
  }

  @Override
  public List<LiftExecutionRow> getExecutionsBetween(LocalDate start, LocalDate end)
      throws Exception {
    return db.getExecutionsBetweenForUser(ownerUserId, start, end);
  }

  @Override
  public ExecutionHistorySummary executionHistorySummary(LocalDate start, LocalDate end)
      throws Exception {
    return db.executionHistorySummaryForUser(ownerUserId, start, end);
  }

  @Override
  public LiftExecution getLastExecution(String liftName, boolean warmup, boolean deload)
      throws Exception {
    return db.getLastExecutionForUser(ownerUserId, liftName, warmup, deload);
  }

  @Override
  public LiftExecution getExecution(String liftName, int executionId) throws Exception {
    return db.getExecutionForUser(ownerUserId, liftName, executionId);
  }

  @Override
  public Map<String, LiftExecution> latestExecutionsByLift() throws Exception {
    return db.latestExecutionsByLiftForUser(ownerUserId);
  }

  @Override
  public Map<String, Boolean> liftEnabledStatuses() throws Exception {
    return db.liftEnabledStatusesForUser(ownerUserId);
  }
}
