package com.group7.airport.ui;

import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.InsetsUIResource;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Makes the whole interface bigger: every font, button, tab and table row.
 * Change the size with  -Dui.fontSize=18  (VM option) or edit DEFAULT_SIZE.
 */
public final class UiTheme {

    public static final int DEFAULT_SIZE = 16;

    private UiTheme() { }

    public static int fontSize() {
        try {
            return Integer.parseInt(System.getProperty("ui.fontSize", String.valueOf(DEFAULT_SIZE)));
        } catch (NumberFormatException e) {
            return DEFAULT_SIZE;
        }
    }

    /* Call once, after setting the look and feel and before creating any window. */
    public static void apply() {
        int size = fontSize();
        List<Object> keys = new ArrayList<>(Collections.list(UIManager.getDefaults().keys()));
        for (Object key : keys) {
            Object value = UIManager.get(key);
            if (value instanceof Font f) {
                UIManager.put(key, new FontUIResource(f.deriveFont((float) size)));
            }
        }
        int pad = Math.max(4, size / 3);
        UIManager.put("Button.margin", new InsetsUIResource(pad, pad * 2, pad, pad * 2));
        UIManager.put("TabbedPane.tabInsets", new InsetsUIResource(pad, pad * 3, pad, pad * 3));
        UIManager.put("TabbedPane.selectedTabPadInsets", new InsetsUIResource(2, 2, 2, 2));
    }
}
