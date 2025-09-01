package org.lift.trax.gui;

import org.lift.trax.Database;
import org.lift.trax.Lift;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class ListPanel extends JPanel {
    public ListPanel(Database db) {
        setLayout(new BorderLayout());
        DefaultListModel<String> model = new DefaultListModel<>();
        try {
            List<Lift> lifts = db.listLifts(null);
            for (Lift lift : lifts) {
                model.addElement(lift.toString());
            }
        } catch (Exception e) {
            model.addElement("Error: " + e.getMessage());
        }
        JList<String> list = new JList<>(model);
        add(new JScrollPane(list), BorderLayout.CENTER);
    }
}
