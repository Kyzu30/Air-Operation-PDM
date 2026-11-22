package com.group7.airport.ui;

import com.group7.airport.dao.ReportDAO;
import com.group7.airport.model.QueryResult;
import com.group7.airport.model.ReportQuery;
import com.group7.airport.util.CsvExporter;
import com.group7.airport.util.ReportQueryLoader;
import com.group7.airport.util.SqlErrors;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

/**
 * "Reports" tab (report screen of assignment section 2.4).
 * <p>
 * The reports are the Task 6 queries, read directly from the group's commented query script
 * ({@code sql/02_AirportOperation_queries.sql}, see {@link ReportQueryLoader}): one source of truth,
 * no second copy of the SQL inside the app.
 * <ul>
 *   <li>left: the list of reports with a search box (searches titles, comments and SQL text);</li>
 *   <li>clicking a report shows its question and SQL and runs it at once;</li>
 *   <li>the SQL can be edited and re-run with Ctrl+Enter - SELECT / WITH only, on a read-only connection;</li>
 *   <li>"Run all → CSV" runs every report and saves report_NN.csv + index.txt (results for report section 8).</li>
 * </ul>
 */
public class ReportsPanel extends JPanel {

    private final ReportDAO dao = new ReportDAO();
    private final DefaultListModel<ReportQuery> model = new DefaultListModel<>();
    private final JList<ReportQuery> list = new JList<>(model);
    private final JTextField filter = new JTextField(12);
    private final JTextArea question = textArea(false);
    private final JTextArea sqlArea = textArea(true);
    private final JLabel status = new JLabel(" ");
    private final ResultTablePanel result = new ResultTablePanel("Result");
    private List<ReportQuery> queries = List.of();
    private ReportQuery current;

    public ReportsPanel() {
        super(new BorderLayout(6, 6));

        JButton run = new JButton("Run (Ctrl+Enter)");
        JButton reset = new JButton("Reset SQL");
        JButton reload = new JButton("Reload file");
        JButton runAll = new JButton("Run all → CSV");
        run.addActionListener(e -> runSql());
        reset.addActionListener(e -> {
            if (current != null) {
                showQuery(current);
            }
        });
        reload.addActionListener(e -> load());
        runAll.addActionListener(e -> exportAll());

        // ---- left: report list
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.addListSelectionListener(e -> {
            ReportQuery q = list.getSelectedValue();
            if (!e.getValueIsAdjusting() && q != null) {
                showQuery(q);
                runSql();
            }
        });
        filter.setToolTipText("Search titles, comments and SQL (e.g. NOT EXISTS, HAVING, Luggage)");
        filter.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { applyFilter(); }
            @Override public void removeUpdate(DocumentEvent e) { applyFilter(); }
            @Override public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });

        JPanel find = new JPanel(new BorderLayout(4, 4));
        find.add(new JLabel("Find"), BorderLayout.WEST);
        find.add(filter, BorderLayout.CENTER);
        JPanel listButtons = new JPanel(new GridLayout(1, 2, 4, 4));
        listButtons.add(reload);
        listButtons.add(runAll);
        JScrollPane listScroll = new JScrollPane(list);
        listScroll.setPreferredSize(new Dimension(320, 200));

        JPanel left = new JPanel(new BorderLayout(4, 4));
        left.setBorder(BorderFactory.createTitledBorder("Task 6 reports"));
        left.add(find, BorderLayout.NORTH);
        left.add(listScroll, BorderLayout.CENTER);
        left.add(listButtons, BorderLayout.SOUTH);

        // ---- right: question + SQL editor + result
        sqlArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK), "run");
        sqlArea.getActionMap().put("run", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                runSql();
            }
        });
        JScrollPane sqlScroll = new JScrollPane(sqlArea);
        sqlScroll.setBorder(BorderFactory.createTitledBorder("SQL (editable – SELECT / WITH only, read-only connection)"));
        JPanel editorButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        editorButtons.add(run);
        editorButtons.add(reset);

        JPanel editor = new JPanel(new BorderLayout(4, 4));
        editor.add(question, BorderLayout.NORTH);
        editor.add(sqlScroll, BorderLayout.CENTER);
        editor.add(editorButtons, BorderLayout.SOUTH);

        JSplitPane right = new JSplitPane(JSplitPane.VERTICAL_SPLIT, editor, result);
        right.setResizeWeight(0.4);
        JSplitPane main = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        main.setResizeWeight(0.25);

        status.setBorder(BorderFactory.createEmptyBorder(0, 6, 2, 6));
        add(main, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);

        load();
    }

    private static JTextArea textArea(boolean code) {
        JTextArea a = new JTextArea(code ? 8 : 2, 50);
        a.setEditable(code);
        a.setLineWrap(!code);
        a.setWrapStyleWord(true);
        if (code) {
            a.setFont(new Font(Font.MONOSPACED, Font.PLAIN, UiTheme.fontSize()));
            a.setTabSize(4);
        } else {
            a.setOpaque(false);
            a.setFont(UIManager.getFont("Label.font"));
            a.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        }
        return a;
    }

    /** Reads the script again (small file, so it is done right here). Problems are shown in the status line. */
    private void load() {
        try {
            ReportQueryLoader.Result r = ReportQueryLoader.load();
            queries = r.queries();
            status.setForeground(UIManager.getColor("Label.foreground"));
            status.setText(queries.size() + " report(s) from " + r.source()
                    + (r.skipped() > 0 ? "   ·   " + r.skipped() + " non-SELECT statement(s) skipped" : ""));
        } catch (IOException ex) {
            queries = List.of();
            status.setForeground(Color.RED);
            status.setText(ex.getMessage().replace('\n', ' '));
        }
        applyFilter();
        if (queries.isEmpty()) {
            question.setText("No report loaded. You can still type a SELECT below and run it.");
        }
    }

    private void applyFilter() {
        String f = filter.getText().trim().toLowerCase(Locale.ROOT);
        model.clear();
        for (ReportQuery q : queries) {
            if (f.isEmpty() || (q.name() + ' ' + q.description() + ' ' + q.sql()).toLowerCase(Locale.ROOT).contains(f)) {
                model.addElement(q);
            }
        }
    }

    private void showQuery(ReportQuery q) {
        current = q;
        question.setText(q.name() + (q.description().isEmpty() ? "" : "\n" + q.description())
                + "   (script line " + q.line() + ")");
        sqlArea.setText(q.sql());
        sqlArea.setCaretPosition(0);
    }

    private void runSql() {
        String sql = sqlArea.getText();
        UiTask.run(this, () -> dao.run(sql), result::show);
    }

    private void exportAll() {
        if (queries.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No report loaded.");
            return;
        }
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Folder for the CSV files");
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path dir = fc.getSelectedFile().toPath();
        List<ReportQuery> batch = List.copyOf(queries);
        UiTask.run(this, () -> exportAll(batch, dir), summary -> {
            JTextArea ta = new JTextArea(summary, 14, 70);
            ta.setEditable(false);
            JOptionPane.showMessageDialog(this, new JScrollPane(ta), "Run all", JOptionPane.INFORMATION_MESSAGE);
        });
    }

    /** Runs every report and writes report_NN.csv + index.txt. One failing query does not stop the others. */
    private String exportAll(List<ReportQuery> batch, Path dir) throws IOException {
        Files.createDirectories(dir);
        StringBuilder index = new StringBuilder();
        int ok = 0;
        for (int i = 0; i < batch.size(); i++) {
            ReportQuery q = batch.get(i);
            String file = String.format("report_%02d.csv", i + 1);
            try {
                QueryResult r = dao.run(q.sql());
                CsvExporter.write(r, dir.resolve(file));
                index.append(String.format("%s  %4d rows  %s%n", file, r.rowCount(), q.name()));
                ok++;
            } catch (SQLException e) {
                index.append(String.format("%s  FAILED     %s -> %s%n", file, q.name(), SqlErrors.friendly(e)));
            } catch (RuntimeException e) {
                index.append(String.format("%s  FAILED     %s -> %s%n", file, q.name(), e.getMessage()));
            }
        }
        Files.writeString(dir.resolve("index.txt"), index, StandardCharsets.UTF_8);
        return ok + " of " + batch.size() + " reports saved to " + dir.toAbsolutePath() + "\n\n" + index;
    }
}
