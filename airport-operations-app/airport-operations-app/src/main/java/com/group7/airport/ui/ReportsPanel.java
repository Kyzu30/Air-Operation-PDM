package com.group7.airport.ui;

import com.group7.airport.dao.ReportDAO;
import com.group7.airport.model.QueryResult;
import com.group7.airport.model.ReportQuery;
import com.group7.airport.util.CsvExporter;
import com.group7.airport.util.ReportQueryLoader;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * "Reports" tab: the queries of sql/02_AirportOperation_queries.sql, including Q1-Q5 of Topic 7.
 * Pick one -> its question, its SQL and its result are shown.
 * The SQL can be edited and re-run (Ctrl+Enter), e.g. to try another date; Reset SQL restores it.
 * Reload file reads the script again; Run all -> CSV saves every result in a chosen folder.
 * Only SELECT / WITH statements run, on a read-only connection (SqlGuard + ReportDAO).
 */
public class ReportsPanel extends JPanel {

    private final ReportDAO dao = new ReportDAO();
    private final JComboBox<ReportQuery> queryBox = new JComboBox<>();
    private final JTextArea desc = new JTextArea(2, 80);
    private final JTextArea sqlArea = new JTextArea(12, 80);
    private final JLabel status = new JLabel(" ");
    private final ResultTablePanel result = new ResultTablePanel("Result");
    private boolean loading;

    public ReportsPanel() {
        super(new BorderLayout(6, 6));

        JButton run = new JButton("Run (Ctrl+Enter)");
        JButton reset = new JButton("Reset SQL");
        JButton reload = new JButton("Reload file");
        JButton runAll = new JButton("Run all → CSV");

        // The combo box sits in CENTER, so a long report title makes the combo narrower
        // instead of pushing the buttons onto a second, hidden row.
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(run);
        buttons.add(reset);
        buttons.add(reload);
        buttons.add(runAll);
        JPanel bar = new JPanel(new BorderLayout(6, 0));
        bar.setBorder(BorderFactory.createEmptyBorder(4, 6, 0, 6));
        bar.add(new JLabel("Report"), BorderLayout.WEST);
        bar.add(queryBox, BorderLayout.CENTER);
        bar.add(buttons, BorderLayout.EAST);

        desc.setEditable(false);
        desc.setLineWrap(true);
        desc.setWrapStyleWord(true);
        desc.setOpaque(false);
        desc.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));

        sqlArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, UiTheme.fontSize()));
        sqlArea.setTabSize(4);
        JScrollPane sqlScroll = new JScrollPane(sqlArea);
        sqlScroll.setBorder(BorderFactory.createTitledBorder("SQL (editable – SELECT only)"));

        // Header (report list + question) keeps its height; only the SQL box shrinks.
        JPanel header = new JPanel(new BorderLayout(4, 4));
        header.add(bar, BorderLayout.NORTH);
        header.add(desc, BorderLayout.CENTER);
        JPanel top = new JPanel(new BorderLayout(4, 4));
        top.add(header, BorderLayout.NORTH);
        top.add(sqlScroll, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, result);
        split.setResizeWeight(0.4);
        add(split, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);

        queryBox.addActionListener(e -> {
            ReportQuery q = (ReportQuery) queryBox.getSelectedItem();
            if (q != null && !loading) {
                showQuery(q);
                runQuery();
            }
        });
        run.addActionListener(e -> runQuery());
        reset.addActionListener(e -> {
            ReportQuery q = (ReportQuery) queryBox.getSelectedItem();
            if (q != null) {
                showQuery(q);
            }
        });
        reload.addActionListener(e -> loadQueries());
        runAll.addActionListener(e -> runAllToCsv());

        sqlArea.getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK), "runQuery");
        sqlArea.getActionMap().put("runQuery", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                runQuery();
            }
        });

        loadQueries();
    }

    private void showQuery(ReportQuery q) {
        desc.setText(q.description().isEmpty() ? q.name() : q.description());
        sqlArea.setText(q.sql());
        sqlArea.setCaretPosition(0);
    }

    private void loadQueries() {
        try {
            ReportQueryLoader.Loaded loaded = ReportQueryLoader.loadWithInfo();
            loading = true;
            queryBox.removeAllItems();
            loaded.queries().forEach(queryBox::addItem);
            loading = false;
            result.clear();
            if (loaded.queries().isEmpty()) {
                desc.setText("No queries found. Put the Task 6 script at " + ReportQueryLoader.DEFAULT_FILE
                        + " (or start with -Dreports.file=<path>), then click Reload file. You can also type a SELECT below.");
                sqlArea.setText("");
            } else {
                queryBox.setSelectedIndex(0);
                showQuery(loaded.queries().get(0));
            }
            status.setText(loaded.queries().size() + " report(s) from " + loaded.source()
                    + (loaded.skipped() > 0 ? "  ·  " + loaded.skipped() + " non-SELECT statement(s) skipped" : ""));
        } catch (IOException ex) {
            loading = false;
            UiTask.showError(this, ex);
        }
    }

    private void runQuery() {
        String sql = sqlArea.getText();
        UiTask.run(this, () -> dao.run(sql), result::show);
    }

    /* Runs every report and saves report_01.csv, report_02.csv, ... + index.txt in a chosen folder. */
    private void runAllToCsv() {
        int count = queryBox.getItemCount();
        if (count == 0) {
            return;
        }
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setDialogTitle("Folder for the report CSV files");
        fc.setSelectedFile(new File("report-results"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path dir = fc.getSelectedFile().toPath();
        List<ReportQuery> all = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            all.add(queryBox.getItemAt(i));
        }
        UiTask.run(this, () -> {
            Files.createDirectories(dir);
            int failed = 0;
            try (PrintWriter idx = new PrintWriter(
                    Files.newBufferedWriter(dir.resolve("index.txt"), StandardCharsets.UTF_8))) {
                for (int i = 0; i < all.size(); i++) {
                    ReportQuery q = all.get(i);
                    String file = String.format("report_%02d.csv", i + 1);
                    try {
                        QueryResult r = dao.run(q.sql());
                        CsvExporter.write(r, dir.resolve(file));
                        idx.printf("%s  %3d row(s)  %s%n", file, r.rowCount(), q.name());
                    } catch (Exception ex) {
                        failed++;
                        idx.printf("%s  FAILED     %s  -> %s%n", file, q.name(), ex.getMessage());
                    }
                }
            }
            return failed;
        }, failed -> JOptionPane.showMessageDialog(this,
                (all.size() - failed) + " of " + all.size() + " reports saved to\n" + dir.toAbsolutePath()
                        + (failed > 0 ? "\n" + failed + " failed – see index.txt" : ""),
                "Run all → CSV", failed > 0 ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE));
    }
}
