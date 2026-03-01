package com.lifttrax.workout;

import com.lifttrax.db.Database;

public class DefaultMaxEffortPlanSource implements MaxEffortPlanSource {
    @Override
    public MaxEffortPlan selectPlan(Database db, MaxEffortLiftPools pools) {
        return MaxEffortPlan.fromDefaults(pools.lowerWeeks(), pools.upperWeeks());
    }
}

