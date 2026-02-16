package com.lifttrax.ui;

import com.lifttrax.db.SqliteDb;

import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.Dimension;

public final class LiftTraxDesktopApp {
    private LiftTraxDesktopApp() {
    }

    public static void main(String[] args) {
        String dbPath = args.length > 0 ? args[0] : "lifts.db";
        SwingUtilities.invokeLater(() -> createAndShowUi(dbPath));
    }

    private static void createAndShowUi(String dbPath) {
        try {
            SqliteDb db = new SqliteDb(dbPath);
            JFrame frame = new JFrame("lift_trax");
            frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            frame.setMinimumSize(new Dimension(900, 650));

            JTabbedPane tabs = new JTabbedPane();
            tabs.addTab("Executions", new ExecutionTabPanel(db));
            frame.setContentPane(tabs);

            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        } catch (Exception error) {
            throw new RuntimeException("Unable to start lift_trax desktop app", error);
        }
    }
}
