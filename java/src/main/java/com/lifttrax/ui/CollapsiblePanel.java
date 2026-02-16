package com.lifttrax.ui;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import java.awt.Component;

public class CollapsiblePanel extends JPanel {
    private final JToggleButton toggle;
    private final JComponent content;

    public CollapsiblePanel(String title, JComponent content) {
        this.content = content;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEtchedBorder());

        toggle = new JToggleButton("▼ " + title, true);
        toggle.setAlignmentX(Component.LEFT_ALIGNMENT);
        toggle.addActionListener(e -> setExpanded(toggle.isSelected()));

        this.content.setAlignmentX(Component.LEFT_ALIGNMENT);

        add(toggle);
        add(this.content);
    }

    public void setExpanded(boolean expanded) {
        content.setVisible(expanded);
        toggle.setText((expanded ? "▼ " : "▶ ") + stripChevron(toggle.getText()));
        revalidate();
        repaint();
    }

    private static String stripChevron(String label) {
        if (label.startsWith("▼ ") || label.startsWith("▶ ")) {
            return label.substring(2);
        }
        return label;
    }
}
