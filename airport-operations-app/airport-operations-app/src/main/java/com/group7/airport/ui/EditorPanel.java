package com.group7.airport.ui;

import com.group7.airport.model.QueryResult;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Map;

/**
 * Shared layout of the editing forms:
 *   upper grid with the current rows  |  form + New / Insert / Update / Delete / Refresh.
 * Clicking a row copies it into the form. Delete always asks for confirmation.
 * Form values are read on the UI thread; validation + SQL run in UiTask (background).
 */
abstract class EditorPanel extends JPanel {

    protected final ResultTablePanel grid;
    protected final JPanel form = new JPanel(new GridBagLayout());
    private int formRow;

    EditorPanel(String gridTitle) {
        super(new BorderLayout(6, 6));
        grid = new ResultTablePanel(gridTitle);
        grid.table().getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                Map<String, Object> row = grid.selectedRow();
                if (row != null) {
                    fillForm(row);
                }
            }
        });

        JButton newBtn = new JButton("New / Clear");
        JButton insert = new JButton("Insert");
        JButton update = new JButton("Update");
        JButton delete = new JButton("Delete");
        JButton refresh = new JButton("Refresh");
        newBtn.addActionListener(e -> {
            grid.table().clearSelection();
            clearForm();
        });
        insert.addActionListener(e -> doInsert());
        update.addActionListener(e -> doUpdate());
        delete.addActionListener(e -> doDelete());
        refresh.addActionListener(e -> reload(null));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(newBtn);
        buttons.add(insert);
        buttons.add(update);
        buttons.add(delete);
        buttons.add(refresh);

        JPanel lower = new JPanel(new BorderLayout());
        lower.setBorder(BorderFactory.createTitledBorder("Record"));
        lower.add(form, BorderLayout.CENTER);
        lower.add(buttons, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, grid, lower);
        split.setResizeWeight(0.6);
        add(split, BorderLayout.CENTER);
    }

    /* Two fields per row. */
    protected void addField(String label, Component field) {
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 6, 4, 6);
        g.anchor = GridBagConstraints.WEST;
        g.gridx = (formRow % 2) * 2;
        g.gridy = formRow / 2;
        form.add(new JLabel(label), g);
        g.gridx++;
        form.add(field, g);
        formRow++;
    }

    protected boolean confirm(String message) {
        return JOptionPane.showConfirmDialog(this, message, "Please confirm",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
    }

    protected void done(String message) {
        JOptionPane.showMessageDialog(this, message, "Done", JOptionPane.INFORMATION_MESSAGE);
    }

    // ------------------------------------------------------------------ combo helpers ("CODE - label")

    protected static void fill(JComboBox<String> box, QueryResult r) {
        box.removeAllItems();
        for (Object[] row : r.rows()) {
            box.addItem(row[0] + (row.length > 1 && row[1] != null ? " - " + row[1] : ""));
        }
    }

    protected static String code(JComboBox<String> box) {
        Object o = box.getSelectedItem();
        if (o == null) {
            return null;
        }
        String s = o.toString();
        int i = s.indexOf(" - ");
        return i < 0 ? s : s.substring(0, i);
    }

    protected static void select(JComboBox<String> box, Object value) {
        String v = value == null ? "" : value.toString();
        for (int i = 0; i < box.getItemCount(); i++) {
            String item = box.getItemAt(i);
            if (item.equals(v) || item.startsWith(v + " - ")) {
                box.setSelectedIndex(i);
                return;
            }
        }
    }

    protected static String text(Object o) {
        return o == null ? "" : o.toString();
    }

    /* Reload the grid; when key != null, select that row afterwards. */
    protected abstract void reload(Object key);

    protected abstract void fillForm(Map<String, Object> row);

    protected abstract void clearForm();

    protected abstract void doInsert();

    protected abstract void doUpdate();

    protected abstract void doDelete();
}
