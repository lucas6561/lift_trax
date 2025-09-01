package org.lift.trax.gui;

import org.lift.trax.Database;

import javax.swing.*;
import java.awt.*;

public class GuiApp extends JFrame {
    private final Database db;

    public GuiApp(Database db) throws Exception {
        super("Lift Trax");
        this.db = db;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 600);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Add", new AddPanel(db));
        tabs.addTab("Query", new QueryPanel());
        tabs.addTab("List", new ListPanel(db));
        add(tabs, BorderLayout.CENTER);
    }

    public static void launch(Database db) {
        SwingUtilities.invokeLater(() -> {
            try {
                GuiApp app = new GuiApp(db);
                app.setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}
