package com.lifttrax.workout;

import com.lifttrax.db.Database;

/**
 * Core DefaultDynamicLiftSource component used by LiftTrax.
 */

public class DefaultDynamicLiftSource implements DynamicLiftSource {
    @Override
    public DynamicLifts select(Database db) throws Exception {
        return DynamicLifts.fromDatabase(db, false);
    }
}

