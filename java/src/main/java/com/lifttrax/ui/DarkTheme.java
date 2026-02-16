package com.lifttrax.ui;

import javax.swing.BorderFactory;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;

final class DarkTheme {
    private DarkTheme() {
    }

    static void apply() {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        FontUIResource font = new FontUIResource(new Font("SansSerif", Font.PLAIN, 13));
        Color background = new Color(20, 23, 28);
        Color panelBackground = new Color(28, 31, 37);
        Color controlBackground = new Color(40, 45, 53);
        Color text = new Color(218, 224, 233);
        Color selection = new Color(16, 116, 170);

        UIManager.put("defaultFont", font);
        UIManager.put("Label.font", font);
        UIManager.put("Button.font", font);
        UIManager.put("TextField.font", font);
        UIManager.put("ComboBox.font", font);
        UIManager.put("List.font", font);

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
        UIManager.put("TabbedPane.selected", new Color(14, 85, 128));
        UIManager.put("TabbedPane.selectedForeground", Color.WHITE);
        UIManager.put("TabbedPane.unselectedBackground", panelBackground);
        UIManager.put("TabbedPane.unselectedForeground", text);
        UIManager.put("TabbedPane.focus", panelBackground);
        UIManager.put("TabbedPane.contentAreaColor", background);
        UIManager.put("TabbedPane.tabInsets", new Insets(4, 10, 4, 10));

        UIManager.put("Label.foreground", text);
        UIManager.put("List.background", controlBackground);
        UIManager.put("List.foreground", text);
        UIManager.put("List.selectionBackground", selection);
        UIManager.put("List.selectionForeground", Color.WHITE);

        UIManager.put("Button.background", controlBackground);
        UIManager.put("Button.foreground", text);
        UIManager.put("Button.margin", new Insets(3, 8, 3, 8));
        UIManager.put("Button.border", BorderFactory.createEmptyBorder(4, 8, 4, 8));

        UIManager.put("ComboBox.background", controlBackground);
        UIManager.put("ComboBox.foreground", text);
        UIManager.put("ComboBox.selectionBackground", selection);
        UIManager.put("ComboBox.selectionForeground", Color.WHITE);
        UIManager.put("ComboBox.buttonBackground", controlBackground);
        UIManager.put("ComboBox.buttonDarkShadow", controlBackground);
        UIManager.put("ComboBox.buttonHighlight", controlBackground);
        UIManager.put("ComboBox.buttonShadow", controlBackground);
        UIManager.put("ComboBox.disabledForeground", new Color(140, 145, 155));

        UIManager.put("TextField.background", new Color(12, 14, 17));
        UIManager.put("TextField.foreground", text);
        UIManager.put("TextField.caretForeground", Color.WHITE);
        UIManager.put("TextField.border", BorderFactory.createLineBorder(new Color(64, 72, 85)));
    }
}
