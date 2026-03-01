package com.lifttrax.workout;

import com.lifttrax.db.Database;

public interface DynamicLiftSource {
    DynamicLifts select(Database db) throws Exception;
}

