package com.lifttrax.workout;

import com.lifttrax.db.Database;

public interface MaxEffortPlanSource {
    MaxEffortPlan selectPlan(Database db, MaxEffortLiftPools pools) throws Exception;
}

