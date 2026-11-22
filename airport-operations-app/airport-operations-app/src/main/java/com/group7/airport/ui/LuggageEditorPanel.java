package com.group7.airport.ui;

import com.group7.airport.dao.LuggageDAO;
import com.group7.airport.util.Validators;
import com.group7.airport.util.Validators.ValidationException;

import javax.swing.JComboBox;
import javax.swing.JTextField;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Insert / update / delete luggage. Weight must be 0 < w <= 32 kg (usual per-bag handling limit;
 * the database CHECK only guarantees w > 0). Every status change is also written to LuggageEvent
 * in the same transaction.
 */
public class LuggageEditorPanel extends EditorPanel {

    static final double MAX_WEIGHT = 32.0;
    private static final String[] STATUSES = {"Checked-in", "Loaded", "Delivered", "Delayed", "Lost"};

    private final LuggageDAO dao = new LuggageDAO();
    private final JTextField luggageId = new JTextField(10);
    private final JTextField passport = new JTextField(12);
    private final JComboBox<String> flight = new JComboBox<>();
    private final JTextField weight = new JTextField(6);
    private final JComboBox<String> status = new JComboBox<>(STATUSES);

    public LuggageEditorPanel() {
        super("Luggage");
        addField("Luggage ID *", luggageId);
        addField("Passport ID *", passport);
        addField("Flight *", flight);
        addField("Weight kg * (0-32]", weight);
        addField("Status *", status);
        UiTask.run(this, dao::flightOptions, r -> fill(flight, r));
        reload(null);
    }

    @Override
    protected void reload(Object key) {
        UiTask.run(this, dao::list, r -> {
            grid.show(r);
            grid.selectWhere("LuggageID", key);
        });
    }

    @Override
    protected void fillForm(Map<String, Object> row) {
        luggageId.setText(text(row.get("LuggageID")));
        passport.setText(text(row.get("PassportID")));
        select(flight, row.get("FlightID"));
        weight.setText(text(row.get("Weight")));
        status.setSelectedItem(text(row.get("Status")));
        luggageId.setEditable(false);
        passport.setEditable(false);
    }

    @Override
    protected void clearForm() {
        luggageId.setEditable(true);
        passport.setEditable(true);
        passport.setText("");
        weight.setText("");
        status.setSelectedIndex(0);
        UiTask.run(this, dao::nextId, luggageId::setText); // suggest the next free ID
        passport.requestFocusInWindow();
    }

    public static BigDecimal validWeight(String raw) {
        return Validators.decimalInRange(raw, "Weight", 0, MAX_WEIGHT);
    }

    @Override
    protected void doInsert() {
        String rawId = luggageId.getText(), rawPass = passport.getText(), w = weight.getText();
        String f = code(flight), st = (String) status.getSelectedItem();
        UiTask.run(this, () -> {
            String id = Validators.pattern(Validators.required(rawId, "Luggage ID", 10).toUpperCase(),
                    "Luggage ID", "LG[0-9]{5}", "Example: LG00099.");
            String pass = Validators.required(rawPass, "Passport ID", 12).toUpperCase();
            dao.insert(pass, id, validWeight(w), st, f);
            return id;
        }, id -> {
            done("Luggage " + id + " added, with its first '" + st + "' event (one transaction, committed).");
            reload(id);
        });
    }

    @Override
    protected void doUpdate() {
        Map<String, Object> row = grid.selectedRow();
        if (row == null) {
            UiTask.showError(this, new ValidationException("Choose a luggage item in the grid first."));
            return;
        }
        String id = text(row.get("LuggageID")), pass = text(row.get("PassportID"));
        String oldStatus = text(row.get("Status"));
        String w = weight.getText(), f = code(flight), st = (String) status.getSelectedItem();
        UiTask.run(this, () -> dao.update(pass, id, validWeight(w), st, f, oldStatus), changed -> {
            done("Luggage " + id + " updated"
                    + (changed ? " and a '" + st + "' event recorded (one transaction, committed)." : "."));
            reload(id);
        });
    }

    @Override
    protected void doDelete() {
        Map<String, Object> row = grid.selectedRow();
        if (row == null) {
            UiTask.showError(this, new ValidationException("Choose a luggage item in the grid first."));
            return;
        }
        String id = text(row.get("LuggageID")), pass = text(row.get("PassportID"));
        if (!confirm("Delete luggage " + id + " of passenger " + pass + " and its event history?")) {
            return;
        }
        UiTask.run(this, () -> {
            dao.delete(pass, id);
            return id;
        }, k -> {
            done("Luggage " + k + " deleted.");
            clearForm();
            reload(null);
        });
    }
}
