package com.lifttrax.ui;

import javax.swing.UIManager;
import java.awt.Color;

final class DarkTheme {
    private DarkTheme() {
    }

    static void apply() {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        Color background = new Color(30, 30, 30);
        Color panelBackground = new Color(36, 36, 36);
        Color controlBackground = new Color(50, 50, 50);
        Color text = new Color(225, 225, 225);
        Color selection = new Color(70, 100, 180);

        UIManager.put("control", panelBackground);
        UIManager.put("info", panelBackground);
        UIManager.put("nimbusBase", controlBackground);
        UIManager.put("nimbusLightBackground", background);
        UIManager.put("text", text);

        UIManager.put("Panel.background", panelBackground);
        UIManager.put("Viewport.background", background);
        UIManager.put("ScrollPane.background", background);
        UIManager.put("TabbedPane.background", panelBackground);
        UIManager.put("TabbedPane.foreground", text);

        UIManager.put("Label.foreground", text);
        UIManager.put("List.background", controlBackground);
        UIManager.put("List.foreground", text);
        UIManager.put("List.selectionBackground", selection);
        UIManager.put("List.selectionForeground", Color.WHITE);

        UIManager.put("Button.background", controlBackground);
        UIManager.put("Button.foreground", text);
        UIManager.put("ComboBox.background", controlBackground);
        UIManager.put("ComboBox.foreground", text);
        UIManager.put("TextField.background", controlBackground);
        UIManager.put("TextField.foreground", text);
    }
}
