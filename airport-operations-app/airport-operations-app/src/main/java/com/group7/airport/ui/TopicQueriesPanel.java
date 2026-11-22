package com.group7.airport.ui;

import com.group7.airport.dao.FlightDAO;
import com.group7.airport.dao.TopicQueryDAO;
import com.group7.airport.util.Validators;
import com.group7.airport.util.Validators.ValidationException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

/* "Topic queries" tab: the five questions of Topic 7, each with its own inputs. */
public class TopicQueriesPanel extends JPanel {

    private static final String[] TITLES = {
        "Q1. Passenger list of a flight on a given date",
        "Q2. Flights delayed by more than N minutes last week",
        "Q3. Total baggage weight carried by each aircraft on a day",
        "Q4. Crew members who worked on EVERY flight of a route in a month",
        "Q5. Passengers whose baggage was loaded but never delivered"
    };
    private static final String[] NOTES = {
        "Flight number = the part of FlightID before '-' (e.g. JJ713). Try JJ713 on 2026-11-14.",
        "Delay = ActualDepartureTime - DepartureTime. Flights that have not left yet are not counted.",
        "\"Yesterday\" by default. Cancelled flights are excluded.",
        "\"For all\" = double NOT EXISTS (division). Try SIN -> SGN, 2026-11.",
        "Uses the LuggageEvent history: a Loaded event exists and no Delivered event exists."
    };

    private final TopicQueryDAO dao = new TopicQueryDAO();
    private final JComboBox<String> which = new JComboBox<>(TITLES);
    private final CardLayout cards = new CardLayout();
    private final JPanel params = new JPanel(cards);
    private final JLabel note = new JLabel(NOTES[0]);
    private final ResultTablePanel result = new ResultTablePanel("Answer");

    private final JTextField q1Flight = new JTextField("JJ713", 8);
    private final JTextField q1Date = new JTextField("2026-11-14", 9);
    private final JTextField q2Ref = new JTextField(LocalDate.now().toString(), 9);
    private final JTextField q2Min = new JTextField("30", 4);
    private final JTextField q3Day = new JTextField(LocalDate.now().minusDays(1).toString(), 9);
    private final JComboBox<String> q4From = new JComboBox<>();
    private final JComboBox<String> q4To = new JComboBox<>();
    private final JTextField q4Month = new JTextField("2026-11", 7);

    public TopicQueriesPanel() {
        super(new BorderLayout(6, 6));
        params.add(row(new JLabel("Flight number"), q1Flight, new JLabel("Date (yyyy-MM-dd)"), q1Date), "0");
        params.add(row(new JLabel("Reference date (\"today\")"), q2Ref,
                new JLabel("Delay more than (minutes)"), q2Min), "1");
        params.add(row(new JLabel("Day (yyyy-MM-dd)"), q3Day), "2");
        params.add(row(new JLabel("From"), q4From, new JLabel("To"), q4To,
                new JLabel("Month (yyyy-MM)"), q4Month), "3");
        params.add(row(new JLabel("(no input)")), "4");

        JButton run = new JButton("Run");
        run.addActionListener(e -> run());
        which.addActionListener(e -> {
            int i = which.getSelectedIndex();
            cards.show(params, String.valueOf(i));
            note.setText(NOTES[i]);
        });

        JPanel top = new JPanel(new BorderLayout(4, 4));
        top.setBorder(BorderFactory.createTitledBorder("Question"));
        top.add(row(which, run), BorderLayout.NORTH);
        top.add(params, BorderLayout.CENTER);
        top.add(note, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);
        add(result, BorderLayout.CENTER);

        UiTask.run(this, new FlightDAO()::airportCodes, codes -> {
            codes.forEach(c -> {
                q4From.addItem(c);
                q4To.addItem(c);
            });
            q4From.setSelectedItem("SIN");
            q4To.setSelectedItem("SGN");
        });
    }

    private static JPanel row(Component... cs) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT));
        for (Component c : cs) {
            p.add(c);
        }
        return p;
    }

    private void run() {
        try {
            switch (which.getSelectedIndex()) {
                case 0 -> {
                    String fn = Validators.pattern(Validators.required(q1Flight.getText(), "Flight number", 8)
                            .toUpperCase(), "Flight number", "[A-Z0-9]{2}[0-9]{1,4}", "Example: VN220 or JJ713.");
                    LocalDate d = Validators.date(q1Date.getText(), "Date");
                    UiTask.run(this, () -> dao.passengerList(fn, d), result::show);
                }
                case 1 -> {
                    LocalDate ref = Validators.date(q2Ref.getText(), "Reference date");
                    int min = Validators.intInRange(q2Min.getText(), "Minutes", 0, 1440);
                    UiTask.run(this, () -> dao.delayedLastWeek(ref, min), result::show);
                }
                case 2 -> {
                    LocalDate d = Validators.date(q3Day.getText(), "Day");
                    UiTask.run(this, () -> dao.baggagePerAircraft(d), result::show);
                }
                case 3 -> {
                    String a = (String) q4From.getSelectedItem();
                    String b = (String) q4To.getSelectedItem();
                    if (a == null || b == null || a.equals(b)) {
                        throw new ValidationException("Choose two different airports.");
                    }
                    YearMonth m;
                    try {
                        m = YearMonth.parse(q4Month.getText().trim());
                    } catch (DateTimeParseException ex) {
                        throw new ValidationException("Month must look like 2026-11.");
                    }
                    UiTask.run(this, () -> dao.crewOnEveryFlight(a, b, m), result::show);
                }
                default -> UiTask.run(this, dao::loadedNeverDelivered, result::show);
            }
        } catch (ValidationException ex) {
            UiTask.showError(this, ex);
        }
    }
}
