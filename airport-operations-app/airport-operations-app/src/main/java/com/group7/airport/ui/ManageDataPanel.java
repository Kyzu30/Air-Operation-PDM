package com.group7.airport.ui;

import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import java.awt.BorderLayout;

/* "Manage data" tab: the editing forms (insert / update / delete). */
public class ManageDataPanel extends JPanel {

    public ManageDataPanel() {
        super(new BorderLayout());
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Passengers", new PassengerEditorPanel());
        tabs.addTab("Flights", new FlightEditorPanel());
        tabs.addTab("Luggage", new LuggageEditorPanel());
        add(tabs, BorderLayout.CENTER);
    }
}
