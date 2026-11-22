package com.group7.airport.ui;

import com.group7.airport.dao.PassengerDAO;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.FlowLayout;

/* "Passengers" tab: search by passport or name, then see tickets and luggage. */
public class PassengerSearchPanel extends JPanel {

    private final PassengerDAO dao = new PassengerDAO();
    private final JTextField keyword = new JTextField(22);
    private final ResultTablePanel passengers = new ResultTablePanel("Passengers");
    private final ResultTablePanel tickets = new ResultTablePanel(null);
    private final ResultTablePanel luggage = new ResultTablePanel(null);

    public PassengerSearchPanel() {
        super(new BorderLayout(6, 6));

        JButton search = new JButton("Search");
        keyword.setToolTipText("Part of passport ID or name, e.g. B559 or Nguyễn");

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bar.add(new JLabel("Passport ID or name"));
        bar.add(keyword);
        bar.add(search);

        JTabbedPane details = new JTabbedPane();
        details.addTab("Tickets", tickets);
        details.addTab("Luggage", luggage);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, passengers, details);
        split.setResizeWeight(0.55);

        add(bar, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);

        search.addActionListener(e -> search());
        keyword.addActionListener(e -> search());
        passengers.table().getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                loadDetails();
            }
        });

        search(); // empty keyword = all passengers
    }

    private void search() {
        String k = keyword.getText();
        UiTask.run(this, () -> dao.search(k), r -> {
            passengers.show(r);
            tickets.clear();
            luggage.clear();
        });
    }

    private void loadDetails() {
        Object id = passengers.selectedValue("PassportID");
        if (id == null) {
            return;
        }
        String passport = id.toString();
        UiTask.run(this, () -> dao.tickets(passport), tickets::show);
        UiTask.run(this, () -> dao.luggage(passport), luggage::show);
    }
}
