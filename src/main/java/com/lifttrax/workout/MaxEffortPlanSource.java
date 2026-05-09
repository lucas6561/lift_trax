package com.lifttrax.workout;

import com.lifttrax.db.Database;

/** Contract for MaxEffortPlanSource behavior used by other LiftTrax components. */
public interface MaxEffortPlanSource {
  MaxEffortPlan selectPlan(Database db, MaxEffortLiftPools pools) throws Exception;
}
