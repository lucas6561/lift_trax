package org.lift.trax;

import java.util.List;

public class Main {
    public static void main(String[] args) throws Exception {
        String dbPath = args.length > 0 ? args[0] : "lift.db";
        SqliteDb db = new SqliteDb(dbPath);
        List<Lift> lifts = db.listLifts(null);
        for (Lift lift : lifts) {
            System.out.println(lift);
            for (LiftExecution exec : lift.executions) {
                System.out.println("  " + exec);
            }
        }
    }
}
