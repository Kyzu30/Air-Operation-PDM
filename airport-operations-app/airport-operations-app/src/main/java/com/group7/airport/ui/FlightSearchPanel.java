package com.group7.airport.ui;

import com.group7.airport.dao.FlightDAO;
import com.group7.airport.model.FlightFilter;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/* "Flights" tab: search flights, then click a flight to see its passengers, crew and baggage (master-detail). */
public class FlightSearchPanel extends JPanel {

    private static final String ANY = "Any";
    private static final String[] STATUSES =
            {ANY, "Scheduled", "Departed", "Arrived", "Delayed", "Cancelled"};

    private final FlightDAO dao = new FlightDAO();
    private final JTextField idField = new JTextField(10);
    private final JComboBox<String> fromBox = new JComboBox<>(new String[]{ANY});
    private final JComboBox<String> toBox = new JComboBox<>(new String[]{ANY});
    private final JComboBox<String> statusBox = new JComboBox<>(STATUSES);
    private final JTextField dateField = new JTextField(9);

    private final ResultTablePanel flights = new ResultTablePanel("Flights");
    private final ResultTablePanel manifest = new ResultTablePanel(null);
    private final ResultTablePanel crew = new ResultTablePanel(null);
    private final ResultTablePanel baggage = new ResultTablePanel(null);

    public FlightSearchPanel() {
        super(new BorderLayout(6, 6));

        JButton search = new JButton("Search");
        JButton reset = new JButton("Reset");
        dateField.setToolTipText("yyyy-MM-dd, e.g. 2026-11-15 (leave empty = any date)");

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bar.add(new JLabel("Flight ID")); bar.add(idField);
        bar.add(new JLabel("From"));      bar.add(fromBox);
        bar.add(new JLabel("To"));        bar.add(toBox);
        bar.add(new JLabel("Status"));    bar.add(statusBox);
        bar.add(new JLabel("Date"));      bar.add(dateField);
        bar.add(search);
        bar.add(reset);

        JTabbedPane details = new JTabbedPane();
        details.addTab("Passengers on selected flight", manifest);
        details.addTab("Crew on selected flight", crew);
        details.addTab("Baggage on selected flight", baggage);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, flights, details);
        split.setResizeWeight(0.6);

        add(bar, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);

        search.addActionListener(e -> search());
        idField.addActionListener(e -> search());
        dateField.addActionListener(e -> search());
        reset.addActionListener(e -> {
            idField.setText("");
            fromBox.setSelectedIndex(0);
            toBox.setSelectedIndex(0);
            statusBox.setSelectedIndex(0);
            dateField.setText("");
            search();
        });
        flights.table().getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                loadDetails();
            }
        });

        loadAirports();
        search(); // show all flights at start
    }

    private void loadAirports() {
        UiTask.run(this, dao::airportCodes, codes -> {
            for (String c : codes) {
                fromBox.addItem(c);
                toBox.addItem(c);
            }
        });
    }

    private void search() {
        LocalDate date = null;
        String d = dateField.getText().trim();
        if (!d.isEmpty()) {
            try {
                date = LocalDate.parse(d);
            } catch (DateTimeParseException ex) {
                JOptionPane.showMessageDialog(this, "Date must be yyyy-MM-dd, e.g. 2026-11-15");
                return;
            }
        }
        FlightFilter filter = new FlightFilter(
                idField.getText(),
                pick(fromBox),
                pick(toBox),
                pick(statusBox),
                date);
        UiTask.run(this, () -> dao.search(filter), r -> {
            flights.show(r);
            manifest.clear();
            crew.clear();
            baggage.clear();
        });
    }

    /* "Any" (first item) means: do not filter on this box. */
    private static String pick(JComboBox<String> box) {
        return box.getSelectedIndex() <= 0 ? null : (String) box.getSelectedItem();
    }

    private void loadDetails() {
        Object id = flights.selectedValue("FlightID");
        if (id == null) {
            return;
        }
        String flightId = id.toString();
        UiTask.run(this, () -> dao.manifest(flightId), manifest::show);
        UiTask.run(this, () -> dao.crew(flightId), crew::show);
        UiTask.run(this, () -> dao.baggage(flightId), baggage::show);
    }
}
