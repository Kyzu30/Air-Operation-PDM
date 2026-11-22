package com.group7.airport.ui;

import com.group7.airport.db.DBConnection;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import java.awt.BorderLayout;

/* Main window after login: one tab per required form. */
public class MainFrame extends JFrame {

    public MainFrame(String username) {
        super("Airport Operations – logged in as " + username);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Flights", new FlightSearchPanel());        // search + master-detail
        tabs.addTab("Passengers", new PassengerSearchPanel());  // search + master-detail
        tabs.addTab("Browse", new BrowsePanel());               // any table + row count
        tabs.addTab("Manage data", new ManageDataPanel());      // insert / update / delete
        tabs.addTab("Topic queries", new TopicQueriesPanel());  // Q1-Q5 of Topic 7
        tabs.addTab("Reports", new ReportsPanel());             // aggregate / Task 6 queries

        JButton logout = new JButton("Log out");
        logout.addActionListener(e -> {
            dispose();
            new LoginFrame().setVisible(true);
        });

        JPanel status = new JPanel(new BorderLayout());
        status.setBorder(BorderFactory.createEmptyBorder(2, 8, 4, 8));
        status.add(new JLabel("User: " + username + "   |   Database: "
                + DBConnection.config().shortUrl()), BorderLayout.WEST);
        status.add(logout, BorderLayout.EAST);

        add(tabs, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);

        setSize(1150, 720);
        setLocationRelativeTo(null);
    }
}
