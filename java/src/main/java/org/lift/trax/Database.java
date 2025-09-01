package org.lift.trax;

import java.util.List;
import java.util.Map;

public interface Database {
    void addLift(String name, LiftRegion region, LiftType main, List<Muscle> muscles, String notes) throws Exception;
    void addLiftExecution(String name, LiftExecution execution) throws Exception;
    void updateLift(String currentName, String newName, LiftRegion region, LiftType main, List<Muscle> muscles, String notes) throws Exception;
    void deleteLift(String name) throws Exception;
    void updateLiftExecution(int execId, LiftExecution execution) throws Exception;
    void deleteLiftExecution(int execId) throws Exception;
    LiftStats liftStats(String name) throws Exception;
    List<Lift> listLifts(String nameFilter) throws Exception;
    List<Lift> liftsByType(LiftType liftType) throws Exception;
    List<Lift> liftsByRegion(LiftRegion region) throws Exception;
    List<Lift> liftsByRegionAndType(LiftRegion region, LiftType liftType) throws Exception;
}
