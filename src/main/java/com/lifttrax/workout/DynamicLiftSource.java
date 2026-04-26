package com.lifttrax.workout;

import com.lifttrax.db.Database;

/**
 * Contract for DynamicLiftSource behavior used by other LiftTrax components.
 */

public interface DynamicLiftSource {
    DynamicLifts select(Database db) throws Exception;
}

