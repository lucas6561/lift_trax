package com.lifttrax.ui;

import com.lifttrax.db.Database;
import com.lifttrax.db.SqliteDb;

import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

public class LiftTraxApp {
    public static void main(String[] args) {
        String dbPath = args.length > 0 ? args[0] : "lifts.db";
        SwingUtilities.invokeLater(() -> {
            DarkTheme.apply();

            try {
                Database database = new SqliteDb(dbPath);
                JFrame frame = new JFrame("Lift Trax");
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.setSize(960, 720);

                JTabbedPane tabs = new JTabbedPane();
                tabs.addTab("Add Execution", new AddExecutionTabPanel(database));
                tabs.addTab("Executions", new ExecutionTabPanel(database));

                frame.setContentPane(tabs);
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            } catch (Exception e) {
                throw new RuntimeException("Failed to start Lift Trax UI", e);
            }
        });
    }
}
