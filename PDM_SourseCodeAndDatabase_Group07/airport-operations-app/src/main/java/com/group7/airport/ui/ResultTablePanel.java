package com.group7.airport.ui;

import com.group7.airport.model.QueryResult;
import com.group7.airport.util.CsvExporter;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.File;
import java.io.IOException;

/**
 * Reusable "table + row count + Export CSV + Show SQL" block.
 * Every tab displays its query results through this class.
 */
public class ResultTablePanel extends JPanel {

    private final DefaultTableModel model = new DefaultTableModel() {
        @Override
        public boolean isCellEditable(int row, int col) {
            return false;
        }
    };
    private final JTable table = new JTable(model);
    private final JLabel info = new JLabel("No data yet.");
    private final JButton exportBtn = new JButton("Export CSV");
    private final JButton sqlBtn = new JButton("Show SQL");
    private QueryResult current;

    public ResultTablePanel(String title) {
        super(new BorderLayout(4, 4));
        if (title != null) {
            setBorder(BorderFactory.createTitledBorder(title));
        }
        table.setAutoCreateRowSorter(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        table.setRowHeight(table.getFontMetrics(table.getFont()).getHeight() + 10);

        exportBtn.setEnabled(false);
        exportBtn.addActionListener(e -> exportCsv());
        sqlBtn.setEnabled(false);
        sqlBtn.setToolTipText("Show the SQL statement that produced this table");
        sqlBtn.addActionListener(e -> showSql());

        JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT));
        south.add(info);
        south.add(exportBtn);
        south.add(sqlBtn);

        add(new JScrollPane(table), BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
    }

    public void show(QueryResult result) {
        current = result;
        Object[][] data = result.rows().toArray(new Object[0][]);
        model.setDataVector(data, result.columns().toArray());
        info.setText(result.rowCount() + " row(s)  ·  " + result.elapsedMs() + " ms");
        exportBtn.setEnabled(true);
        sqlBtn.setEnabled(result.sql() != null);
    }

    public void clear() {
        current = null;
        model.setDataVector(new Object[0][], new Object[0]);
        info.setText("No data yet.");
        exportBtn.setEnabled(false);
        sqlBtn.setEnabled(false);
    }

    /* Selected row as a column -> value map, or null when nothing is selected. */
    public java.util.Map<String, Object> selectedRow() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0 || current == null) {
            return null;
        }
        Object[] row = current.rows().get(table.convertRowIndexToModel(viewRow));
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        for (int i = 0; i < row.length; i++) {
            m.put(current.columns().get(i), row[i]);
        }
        return m;
    }

    /* Selects the row whose column has this value (used after insert/update). */
    public void selectWhere(String column, Object value) {
        if (current == null || value == null) {
            return;
        }
        int idx = current.columns().indexOf(column);
        for (int m = 0; idx >= 0 && m < current.rowCount(); m++) {
            if (value.toString().equals(String.valueOf(current.rows().get(m)[idx]))) {
                int v = table.convertRowIndexToView(m);
                table.setRowSelectionInterval(v, v);
                table.scrollRectToVisible(table.getCellRect(v, 0, true));
                return;
            }
        }
    }

    private void showSql() {
        if (current == null || current.sql() == null) {
            return;
        }
        javax.swing.JTextArea ta = new javax.swing.JTextArea(current.sql().strip());
        ta.setEditable(false);
        ta.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, UiTheme.fontSize()));
        JScrollPane sp = new JScrollPane(ta);
        sp.setPreferredSize(new java.awt.Dimension(760, 320));
        javax.swing.JOptionPane.showMessageDialog(this, sp, "SQL behind this table",
                javax.swing.JOptionPane.PLAIN_MESSAGE);
    }

    public JTable table() {
        return table;
    }

    /* Value of a column in the selected row (works after sorting), or null. */
    public Object selectedValue(String column) {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0 || current == null) {
            return null;
        }
        int modelRow = table.convertRowIndexToModel(viewRow);
        return current.value(modelRow, column);
    }

    private void exportCsv() {
        if (current == null) {
            return;
        }
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("result.csv"));
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                CsvExporter.write(current, fc.getSelectedFile().toPath());
                info.setText(info.getText() + "  ·  saved " + fc.getSelectedFile().getName());
            } catch (IOException ex) {
                UiTask.showError(this, ex);
            }
        }
    }
}
