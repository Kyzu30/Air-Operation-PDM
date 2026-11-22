package com.group7.airport.ui;

import com.group7.airport.dao.BrowseDAO;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;

/* "Browse" tab: every row of one table in a grid, with the number of rows. */
public class BrowsePanel extends JPanel {

    private final BrowseDAO dao = new BrowseDAO();
    private final JComboBox<String> table = new JComboBox<>(BrowseDAO.TABLES.toArray(new String[0]));
    private final ResultTablePanel result = new ResultTablePanel(null);

    public BrowsePanel() {
        super(new BorderLayout(6, 6));
        JButton load = new JButton("Load");
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bar.add(new JLabel("Table"));
        bar.add(table);
        bar.add(load);
        add(bar, BorderLayout.NORTH);
        add(result, BorderLayout.CENTER);

        load.addActionListener(e -> load());
        table.addActionListener(e -> load());
        load();
    }

    private void load() {
        String t = (String) table.getSelectedItem();
        UiTask.run(this, () -> dao.browse(t), result::show);
    }
}
