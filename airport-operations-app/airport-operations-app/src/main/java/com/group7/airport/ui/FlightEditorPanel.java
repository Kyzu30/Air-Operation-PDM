package com.group7.airport.ui;

import com.group7.airport.dao.FlightDAO;
import com.group7.airport.dao.FlightDAO.FlightRow;
import com.group7.airport.util.Validators;
import com.group7.airport.util.Validators.ValidationException;

import javax.swing.JComboBox;
import javax.swing.JTextField;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Insert / update / delete flights.
 * Checked before saving: required fields, FlightID format, arrival after departure,
 * Date = day of departure, different airports, actual departure only for Departed/Arrived
 * flights, and (with a query) that the aircraft is free at that time.
 */
public class FlightEditorPanel extends EditorPanel {

    private static final String[] STATUSES = {"Scheduled", "Departed", "Arrived", "Delayed", "Cancelled"};

    private final FlightDAO dao = new FlightDAO();
    private final JTextField flightId = new JTextField(12);
    private final JTextField date = new JTextField(10);
    private final JTextField dep = new JTextField(14);
    private final JTextField actual = new JTextField(14);
    private final JTextField arr = new JTextField(14);
    private final JComboBox<String> status = new JComboBox<>(STATUSES);
    private final JComboBox<String> from = new JComboBox<>();
    private final JComboBox<String> to = new JComboBox<>();
    private final JComboBox<String> aircraft = new JComboBox<>();
    private final JComboBox<String> gate = new JComboBox<>();

    public FlightEditorPanel() {
        super("Flights");
        addField("Flight ID * (e.g. VN260-1112)", flightId);
        addField("Date * (yyyy-MM-dd)", date);
        addField("Departure * (yyyy-MM-dd HH:mm)", dep);
        addField("Arrival * (yyyy-MM-dd HH:mm)", arr);
        addField("Actual departure (if left)", actual);
        addField("Status *", status);
        addField("From *", from);
        addField("To *", to);
        addField("Aircraft *", aircraft);
        addField("Gate *", gate);

        UiTask.run(this, dao::airportCodes, codes -> codes.forEach(c -> {
            from.addItem(c);
            to.addItem(c);
        }));
        UiTask.run(this, dao::aircraftOptions, r -> fill(aircraft, r));
        UiTask.run(this, dao::gateOptions, r -> fill(gate, r));
        reload(null);
    }

    @Override
    protected void reload(Object key) {
        UiTask.run(this, dao::listForEdit, r -> {
            grid.show(r);
            grid.selectWhere("FlightID", key);
        });
    }

    @Override
    protected void fillForm(Map<String, Object> row) {
        flightId.setText(text(row.get("FlightID")));
        date.setText(text(row.get("Date")));
        dep.setText(Validators.display(row.get("DepartureTime")));
        actual.setText(Validators.display(row.get("ActualDepartureTime")));
        arr.setText(Validators.display(row.get("ArrivalTime")));
        status.setSelectedItem(text(row.get("Status")));
        select(from, row.get("DepartureAirportCode"));
        select(to, row.get("ArrivalAirportCode"));
        select(aircraft, row.get("AircraftID"));
        select(gate, row.get("GateID"));
        flightId.setEditable(false);
    }

    @Override
    protected void clearForm() {
        for (JTextField f : new JTextField[]{flightId, date, dep, actual, arr}) {
            f.setText("");
        }
        status.setSelectedIndex(0);
        flightId.setEditable(true);
        flightId.requestFocusInWindow();
    }

    /* Pure validation of the form text (no database). Public so the integration test can call it. */
    public static FlightRow validate(String id, String dateText, String depText, String actualText, String arrText,
                              String st, String fromCode, String toCode, String aircraftId, String gateId) {
        LocalDate d = Validators.date(dateText, "Date");
        LocalDateTime departure = Validators.dateTime(depText, "Departure");
        LocalDateTime arrival = Validators.dateTime(arrText, "Arrival");
        if (fromCode == null || toCode == null || aircraftId == null || gateId == null) {
            throw new ValidationException("Choose the airports, the aircraft and the gate.");
        }
        Validators.dateOrder(departure, arrival, "Arrival must be after departure.");
        if (!departure.toLocalDate().equals(d)) {
            throw new ValidationException("Date must be the day of departure (" + departure.toLocalDate() + ").");
        }
        if (fromCode.equals(toCode)) {
            throw new ValidationException("Departure and arrival airports must be different.");
        }
        boolean hasLeft = "Departed".equals(st) || "Arrived".equals(st);
        LocalDateTime act = null;
        if (actualText == null || actualText.isBlank()) {
            if (hasLeft) {
                throw new ValidationException("Status " + st + " needs an actual departure time.");
            }
        } else {
            if (!hasLeft) {
                throw new ValidationException("Status " + st + " means the flight has not left yet: leave Actual "
                        + "departure empty, or change Status to Departed / Arrived.");
            }
            act = Validators.dateTime(actualText, "Actual departure");
            if (act.isBefore(departure.minusHours(1)) || act.isAfter(departure.plusHours(24))) {
                throw new ValidationException("Actual departure must be between 1 hour before and 24 hours after "
                        + "the scheduled departure.");
            }
        }
        return new FlightRow(id, d, departure, act, arrival, st, fromCode, toCode, aircraftId, gateId);
    }

    private FlightRow readForm(String id) {
        return validate(id, date.getText(), dep.getText(), actual.getText(), arr.getText(),
                (String) status.getSelectedItem(), code(from), code(to), code(aircraft), code(gate));
    }

    @Override
    protected void doInsert() {
        String raw = flightId.getText();
        String id;
        FlightRow f;
        try {
            id = Validators.pattern(Validators.required(raw, "Flight ID", 20).toUpperCase(), "Flight ID",
                    "[A-Z0-9]{2}[0-9]{1,4}-[0-9]{4}", "Example: VN260-1112 (airline + number - MMDD).");
            f = readForm(id);
        } catch (ValidationException ex) {
            UiTask.showError(this, ex);
            return;
        }
        UiTask.run(this, () -> {
            dao.insert(f);
            return f.flightId();
        }, k -> {
            done("Flight " + k + " added.");
            reload(k);
        });
    }

    @Override
    protected void doUpdate() {
        Map<String, Object> row = grid.selectedRow();
        FlightRow f;
        try {
            if (row == null) {
                throw new ValidationException("Choose a flight in the grid first.");
            }
            f = readForm(text(row.get("FlightID")));
        } catch (ValidationException ex) {
            UiTask.showError(this, ex);
            return;
        }
        UiTask.run(this, () -> {
            dao.update(f);
            return f.flightId();
        }, k -> {
            done("Flight " + k + " updated.");
            reload(k);
        });
    }

    @Override
    protected void doDelete() {
        Map<String, Object> row = grid.selectedRow();
        if (row == null) {
            UiTask.showError(this, new ValidationException("Choose a flight in the grid first."));
            return;
        }
        String id = text(row.get("FlightID"));
        if (!confirm("Delete flight " + id + "?")) {
            return;
        }
        UiTask.run(this, () -> dao.delete(id), n -> {
            done("Flight " + id + " deleted.");
            clearForm();
            reload(null);
        });
    }
}
