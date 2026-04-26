package com.lifttrax.workout;

import com.lifttrax.db.Database;
import com.lifttrax.models.LiftType;

/**
 * Core SwingMaxEffortPlanSource component used by LiftTrax.
 */

public class SwingMaxEffortPlanSource implements MaxEffortPlanSource {
    @Override
    public MaxEffortPlan selectPlan(Database db, MaxEffortLiftPools pools) throws Exception {
        return MaxEffortEditor.editPlan(
                db.liftsByType(LiftType.SQUAT),
                db.liftsByType(LiftType.DEADLIFT),
                db.liftsByType(LiftType.BENCH_PRESS),
                db.liftsByType(LiftType.OVERHEAD_PRESS),
                pools.lowerWeeks(),
                pools.upperWeeks()
        );
    }
}

