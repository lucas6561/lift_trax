package com.lifttrax.db;

import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.LiftRegion;
import com.lifttrax.models.LiftStats;
import com.lifttrax.models.LiftType;
import com.lifttrax.models.Muscle;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Contract for Database behavior used by other LiftTrax components. */
public interface Database {
  void addLift(String name, LiftRegion region, LiftType main, List<Muscle> muscles, String notes)
      throws Exception;

  void addLiftExecution(String name, LiftExecution execution) throws Exception;

  void updateLift(
      String currentName,
      String newName,
      LiftRegion region,
      LiftType main,
      List<Muscle> muscles,
      String notes)
      throws Exception;

  void deleteLift(String name) throws Exception;

  void setLiftEnabled(String name, boolean enabled) throws Exception;

  boolean isLiftEnabled(String name) throws Exception;

  List<LiftExecution> getExecutions(String liftName) throws Exception;

  default Map<String, List<LiftExecution>> getExecutionsByLift(Collection<String> liftNames)
      throws Exception {
    Map<String, List<LiftExecution>> executionsByLift = new LinkedHashMap<>();
    for (String liftName : liftNames) {
      if (!executionsByLift.containsKey(liftName)) {
        executionsByLift.put(liftName, getExecutions(liftName));
      }
    }
    return executionsByLift;
  }

  void updateLiftExecution(int execId, LiftExecution execution) throws Exception;

  void deleteLiftExecution(int execId) throws Exception;

  LiftStats liftStats(String name) throws Exception;

  List<Lift> listLifts() throws Exception;

  Lift getLift(String name) throws Exception;

  List<Lift> liftsByType(LiftType liftType) throws Exception;

  List<Lift> getAccessoriesByMuscle(Muscle muscle) throws Exception;

  List<Lift> liftsByRegionAndType(LiftRegion region, LiftType liftType) throws Exception;
}
