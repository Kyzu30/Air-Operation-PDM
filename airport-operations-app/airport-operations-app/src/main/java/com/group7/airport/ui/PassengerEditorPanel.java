package com.group7.airport.ui;

import com.group7.airport.dao.PassengerDAO;
import com.group7.airport.util.Validators;

import javax.swing.JTextField;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/* Insert / update / delete passengers (Passenger + PassengerPhone in one transaction). */
public class PassengerEditorPanel extends EditorPanel {

    private final PassengerDAO dao = new PassengerDAO();
    private final JTextField passport = new JTextField(14);
    private final JTextField name = new JTextField(22);
    private final JTextField email = new JTextField(22);
    private final JTextField phones = new JTextField(22);

    public PassengerEditorPanel() {
        super("Passengers");
        addField("Passport ID *", passport);
        addField("Full name *", name);
        addField("Email", email);
        addField("Phones (comma-separated)", phones);
        reload(null);
    }

    @Override
    protected void reload(Object key) {
        UiTask.run(this, () -> dao.search(""), r -> {
            grid.show(r);
            grid.selectWhere("PassportID", key);
        });
    }

    @Override
    protected void fillForm(Map<String, Object> row) {
        passport.setText(text(row.get("PassportID")));
        name.setText(text(row.get("Name")));
        email.setText(text(row.get("Email")));
        phones.setText(text(row.get("Phones")));
        passport.setEditable(false); // the key is never changed by Update
    }

    @Override
    protected void clearForm() {
        passport.setText("");
        name.setText("");
        email.setText("");
        phones.setText("");
        passport.setEditable(true);
        passport.requestFocusInWindow();
    }

    // validation

    public static String validPassport(String raw) {
        String p = Validators.required(raw, "Passport ID", 12).toUpperCase();
        return Validators.pattern(p, "Passport ID", "[A-Z0-9]{6,12}", "Use 6-12 letters/digits, e.g. B5594418.");
    }

    public static String validEmail(String raw) {
        String e = Validators.optional(raw, "Email", 100);
        return e == null ? null : Validators.pattern(e, "Email", "[^@\\s]+@[^@\\s]+\\.[^@\\s]+",
                "Example: name@gmail.com");
    }

    public static List<String> validPhones(String raw) {
        Set<String> out = new LinkedHashSet<>();
        for (String part : raw.split(",")) {
            String p = part.trim().replace(" ", "");
            if (!p.isEmpty()) {
                out.add(Validators.pattern(p, "Phone '" + p + "'", "\\+?[0-9]{8,15}",
                        "Use 8-15 digits, optional leading +."));
            }
        }
        return new ArrayList<>(out);
    }

    // actions

    @Override
    protected void doInsert() {
        String p = passport.getText(), n = name.getText(), e = email.getText(), ph = phones.getText();
        UiTask.run(this, () -> {
            String id = validPassport(p);
            dao.insert(id, Validators.required(n, "Full name", 100), validEmail(e), validPhones(ph));
            return id;
        }, id -> {
            done("Passenger " + id + " added with its phone numbers (one transaction, committed).");
            reload(id);
        });
    }

    @Override
    protected void doUpdate() {
        Map<String, Object> row = grid.selectedRow();
        if (row == null) {
            UiTask.showError(this, new Validators.ValidationException("Choose a passenger in the grid first."));
            return;
        }
        String id = text(row.get("PassportID"));
        String n = name.getText(), e = email.getText(), ph = phones.getText();
        UiTask.run(this, () -> {
            dao.update(id, Validators.required(n, "Full name", 100), validEmail(e), validPhones(ph));
            return id;
        }, k -> {
            done("Passenger " + k + " updated (one transaction, committed).");
            reload(k);
        });
    }

    @Override
    protected void doDelete() {
        Map<String, Object> row = grid.selectedRow();
        if (row == null) {
            UiTask.showError(this, new Validators.ValidationException("Choose a passenger in the grid first."));
            return;
        }
        String id = text(row.get("PassportID"));
        if (!confirm("Delete passenger " + id + " (" + row.get("Name") + ")?\n"
                + "Their phone numbers are deleted with them (ON DELETE CASCADE).")) {
            return;
        }
        UiTask.run(this, () -> dao.delete(id), n -> {
            done("Passenger " + id + " deleted.");
            clearForm();
            reload(null);
        });
    }
}
