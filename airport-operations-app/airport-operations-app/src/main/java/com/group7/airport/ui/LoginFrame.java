package com.group7.airport.ui;

import com.group7.airport.dao.UserDAO;
import com.group7.airport.db.DBConnection;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Arrays;

/* Login screen: checks Username + password against the AppUser table. */
public class LoginFrame extends JFrame {

    private final JTextField userField = new JTextField(18);
    private final JPasswordField passField = new JPasswordField(18);
    private final JLabel status = new JLabel(" ", SwingConstants.CENTER);
    private final JButton loginBtn = new JButton("Log in");
    private final UserDAO userDAO = new UserDAO();

    public LoginFrame() {
        super("Airport Operations – Login");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JLabel title = new JLabel("Airport Operations System", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() * 1.4f));
        JLabel sub = new JLabel("Group 7 · Principles of Database Management", SwingConstants.CENTER);

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6, 6, 6, 6);
        g.anchor = GridBagConstraints.WEST;
        g.gridx = 0; g.gridy = 0; form.add(new JLabel("Username"), g);
        g.gridx = 1;               form.add(userField, g);
        g.gridx = 0; g.gridy = 1; form.add(new JLabel("Password"), g);
        g.gridx = 1;               form.add(passField, g);
        g.gridx = 1; g.gridy = 2; g.anchor = GridBagConstraints.EAST; form.add(loginBtn, g);

        JPanel header = new JPanel(new BorderLayout());
        header.add(title, BorderLayout.NORTH);
        header.add(sub, BorderLayout.SOUTH);

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(16, 20, 12, 20));
        root.add(header, BorderLayout.NORTH);
        root.add(form, BorderLayout.CENTER);
        root.add(status, BorderLayout.SOUTH);
        setContentPane(root);

        loginBtn.addActionListener(e -> doLogin());
        passField.addActionListener(e -> doLogin()); // Enter key
        getRootPane().setDefaultButton(loginBtn);

        pack();
        setLocationRelativeTo(null);
        checkConnection();
    }

    /* Shows right away whether MySQL + AppUser are ready, so setup problems are obvious. */
    private void checkConnection() {
        status.setForeground(Color.GRAY);
        status.setText("Connecting to " + DBConnection.config().shortUrl() + " ...");
        UiTask.run(this, userDAO::appUserTableExists, exists -> {
            if (exists) {
                status.setForeground(new Color(0, 128, 0));
                status.setText("Connected to database.");
            } else {
                status.setForeground(Color.RED);
                status.setText("Connected, but table AppUser is missing – run sql/01_appuser_setup.sql");
            }
        });
    }

    private void doLogin() {
        String user = userField.getText();
        char[] pass = passField.getPassword();
        loginBtn.setEnabled(false);
        UiTask.run(this, () -> userDAO.authenticate(user, pass), ok -> {
            if (ok) {
                new MainFrame(user.trim()).setVisible(true);
                dispose();
            } else {
                status.setForeground(Color.RED);
                status.setText("Wrong username or password.");
                passField.setText("");
                passField.requestFocusInWindow();
            }
        }, () -> {
            Arrays.fill(pass, '\0');
            loginBtn.setEnabled(true);
        });
    }
}
