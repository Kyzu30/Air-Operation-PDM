package com.group7.airport;

import com.group7.airport.ui.LoginFrame;
import com.group7.airport.ui.UiTheme;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/* Entry point: opens the login window. */
public class App {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // default look and feel is fine
            }
            UiTheme.apply(); // bigger fonts, buttons and tabs
            new LoginFrame().setVisible(true);
        });
    }
}
