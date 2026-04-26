package com.lifttrax.workout;

import com.lifttrax.db.Database;

/**
 * Core SwingDynamicLiftSource component used by LiftTrax.
 */

public class SwingDynamicLiftSource implements DynamicLiftSource {
    @Override
    public DynamicLifts select(Database db) throws Exception {
        return DynamicLifts.fromDatabase(db, true);
    }
}

