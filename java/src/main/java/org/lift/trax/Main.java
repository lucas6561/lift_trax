package org.lift.trax;

import org.lift.trax.gui.GuiApp;

public class Main {
    public static void main(String[] args) throws Exception {
        String dbPath = args.length > 0 ? args[0] : "lift.db";
        SqliteDb db = new SqliteDb(dbPath);
        GuiApp.launch(db);
    }
}
