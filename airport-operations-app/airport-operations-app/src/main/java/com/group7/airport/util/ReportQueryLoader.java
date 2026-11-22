package com.group7.airport.util;

import com.group7.airport.model.ReportQuery;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the Task 6 query script (the "second, commented script" of assignment section 2.3)
 * into the reports of the Reports tab. The script is used exactly as the Task 6 team wrote it,
 * so there is only one copy of every query and nothing to keep in sync.
 *
 * <h2>Where the script is read from</h2>
 * <ol>
 *   <li>{@code -Dreports.file=<path>} if given, otherwise {@value #DEFAULT_FILE}
 *       (relative to the folder the app is started in, i.e. the project folder);</li>
 *   <li>the copy that Maven puts inside the jar ({@value #RESOURCE}).</li>
 * </ol>
 *
 * <h2>How the script is read</h2>
 * <ul>
 *   <li>Statements end with {@code ;} (or the current {@code DELIMITER}); quotes and comments are respected.</li>
 *   <li>The title of a report is the comment paragraph written just above the query: its first line is
 *       the title, the other lines the description. {@code -- @name:} / {@code -- @desc:} tags are also
 *       accepted. A comment on the same line after a {@code ;} is ignored.</li>
 *   <li>{@code SELECT}, {@code WITH} and {@code (SELECT ...) UNION ...} statements become reports.</li>
 *   <li>{@code CREATE [OR REPLACE] VIEW v AS SELECT ...} becomes a report that runs the view's SELECT,
 *       so it works even when the view has not been created in this database.</li>
 *   <li>Everything else (USE, SET, DROP, INSERT / UPDATE / DELETE, transactions, procedures) is skipped
 *       and counted: the Reports tab never changes data.</li>
 * </ul>
 */
public final class ReportQueryLoader {

    public static final String DEFAULT_FILE = "sql/02_AirportOperation_queries.sql";
    public static final String RESOURCE = "/queries/02_AirportOperation_queries.sql";

    /** What was loaded, from where, and how many statements were not reports. */
    public record Result(String source, List<ReportQuery> queries, int skipped) { }

    private static final Pattern READ_ONLY = Pattern.compile("(?i)^(SELECT\\b|WITH\\b|\\()");
    private static final Pattern VIEW = Pattern.compile(
            "(?is)^CREATE\\s+(?:OR\\s+REPLACE\\s+)?(?:ALGORITHM\\s*=\\s*\\w+\\s+)?(?:DEFINER\\s*=\\s*\\S+\\s+)?"
            + "(?:SQL\\s+SECURITY\\s+\\w+\\s+)?VIEW\\s+([`\\w.]+)(?:\\s*\\([^)]*\\))?\\s+AS\\s+(.+?)"
            + "(?:\\s+WITH\\s+(?:CASCADED\\s+|LOCAL\\s+)?CHECK\\s+OPTION)?$");
    private static final Pattern DROP_VIEW = Pattern.compile("(?i)^DROP\\s+VIEW\\b");
    private static final Pattern DELIMITER = Pattern.compile("(?i)^DELIMITER\\s+(\\S+)\\s*$");
    private static final Pattern DECORATION = Pattern.compile("^[\\s=\\-*#_~+/]*$");
    private static final int MAX_TITLE = 100;

    private ReportQueryLoader() { }

    public static Path configuredFile() {
        return Path.of(System.getProperty("reports.file", DEFAULT_FILE));
    }

    public static Result load() throws IOException {
        Path file = configuredFile();
        if (Files.isRegularFile(file)) {
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                return parse(r, file.toAbsolutePath().normalize().toString());
            }
        }
        try (InputStream in = ReportQueryLoader.class.getResourceAsStream(RESOURCE)) {
            if (in != null) {
                return parse(new InputStreamReader(in, StandardCharsets.UTF_8), "classpath:" + RESOURCE);
            }
        }
        throw new FileNotFoundException("Task 6 query script not found: " + file.toAbsolutePath().normalize()
                + "\nPut the script there, or start the app with -Dreports.file=<path to the .sql file>.");
    }

    // ------------------------------------------------------------------ parser

    public static Result parse(Reader reader, String source) throws IOException {
        StringWriter all = new StringWriter();
        reader.transferTo(all);
        return new Parser(all.toString()).run(source);
    }

    /** Single pass over the text. Outside a statement it collects comments; inside, it looks for the delimiter. */
    private static final class Parser {
        private final String s;
        private final List<ReportQuery> out = new ArrayList<>();
        private final List<String> paragraph = new ArrayList<>(); // last comment paragraph
        private int skipped;
        private int i;
        private int line = 1;
        private String delimiter = ";";
        private boolean gap;          // a blank line since the last comment line -> next comment starts a new paragraph
        private boolean lineHasText;  // something other than whitespace on the current line
        private boolean afterStatement; // still on the line where the last statement ended
        private List<String> carry = List.of(); // title of a skipped "DROP VIEW", reused by the CREATE VIEW after it

        Parser(String s) {
            this.s = s.replace("\r\n", "\n").replace('\r', '\n');
        }

        Result run(String source) {
            int n = s.length();
            while (i < n) {
                char c = s.charAt(i);
                if (c == '\n') {
                    if (!lineHasText) {
                        gap = true;
                    }
                    newLine();
                    i++;
                } else if (Character.isWhitespace(c)) {
                    i++;
                } else if (s.startsWith("--", i) && (i + 2 >= n || Character.isWhitespace(s.charAt(i + 2))) || c == '#') {
                    int end = indexOrEnd('\n', i);
                    comment(s.substring(i + (c == '#' ? 1 : 2), end));
                    i = end;
                } else if (s.startsWith("/*", i)) {
                    int end = s.indexOf("*/", i + 2);
                    end = end < 0 ? n : end;
                    String body = s.substring(i + 2, end);
                    for (String part : body.split("\n", -1)) {
                        comment(part.replaceFirst("^\\s*\\*", ""));
                    }
                    line += count(body, '\n');
                    i = Math.min(n, end + 2);
                } else if (!lineHasText && startsDelimiterCommand()) {
                    int end = indexOrEnd('\n', i);
                    Matcher m = DELIMITER.matcher(s.substring(i, end).trim());
                    if (m.matches()) {
                        delimiter = m.group(1);
                    }
                    lineHasText = true;
                    i = end;
                } else {
                    statement();
                }
            }
            return new Result(source, List.copyOf(out), skipped);
        }

        private void newLine() {
            line++;
            lineHasText = false;
            afterStatement = false;
        }

        private void comment(String text) {
            lineHasText = true;
            if (afterStatement) {
                return; // "SELECT ...;  -- note" belongs to the query before, not to the next one
            }
            String t = text.trim();
            if (t.endsWith("*/")) {
                t = t.substring(0, t.length() - 2).trim();
            }
            if (DECORATION.matcher(t).matches()) {
                return; // empty line or a ===== / ----- banner line
            }
            if (gap) {
                paragraph.clear();
                gap = false;
            }
            paragraph.add(t);
        }

        private boolean startsDelimiterCommand() {
            return s.regionMatches(true, i, "DELIMITER", 0, 9)
                    && i + 9 < s.length() && Character.isWhitespace(s.charAt(i + 9));
        }

        /** Reads one statement up to the delimiter, skipping quoted text and comments inside it. */
        private void statement() {
            int start = i;
            int startLine = line;
            List<String> header = paragraph.isEmpty() ? carry : List.copyOf(paragraph);
            carry = List.of();
            paragraph.clear();
            gap = false;
            int n = s.length();
            int end = n;
            while (i < n) {
                char c = s.charAt(i);
                if (s.startsWith(delimiter, i)) {
                    end = i;
                    i += delimiter.length();
                    break;
                }
                if (c == '\'' || c == '"' || c == '`') {
                    int j = i + 1;
                    while (j < n && s.charAt(j) != c) {
                        if (s.charAt(j) == '\\') {
                            j++;
                        }
                        j++;
                    }
                    line += count(s.substring(i, Math.min(j, n)), '\n');
                    i = Math.min(n, j + 1);
                } else if (s.startsWith("--", i) && (i + 2 >= n || Character.isWhitespace(s.charAt(i + 2))) || c == '#') {
                    i = indexOrEnd('\n', i);
                } else if (s.startsWith("/*", i)) {
                    int e = s.indexOf("*/", i + 2);
                    e = e < 0 ? n : e + 2;
                    line += count(s.substring(i, e), '\n');
                    i = e;
                } else {
                    if (c == '\n') {
                        line++;
                    }
                    i++;
                }
            }
            lineHasText = true;
            afterStatement = true;
            add(header, s.substring(start, end).strip(), startLine);
        }

        private void add(List<String> header, String sql, int startLine) {
            if (sql.isEmpty()) {
                return;
            }
            String plain = SqlGuard.stripComments(sql).strip();
            String name = null;
            List<String> desc = new ArrayList<>();
            for (String h : header) {
                if (h.regionMatches(true, 0, "@name:", 0, 6)) {
                    name = h.substring(6).trim();
                } else if (h.regionMatches(true, 0, "@desc:", 0, 6)) {
                    desc.add(h.substring(6).trim());
                } else if (name == null && desc.isEmpty()) {
                    name = h;
                } else {
                    desc.add(h);
                }
            }
            if (READ_ONLY.matcher(plain).lookingAt()) {
                // keep the text as written (comments inside the query stay visible in the editor)
            } else {
                Matcher v = VIEW.matcher(plain);
                if (!v.matches()) {
                    skipped++;
                    if (DROP_VIEW.matcher(plain).lookingAt()) {
                        carry = header; // "-- Q12 ..." above "DROP VIEW IF EXISTS v; CREATE VIEW v AS ..."
                    }
                    return;
                }
                String view = v.group(1).replace("`", "");
                name = name == null ? "View " + view : name + "  [view " + view + "]";
                sql = v.group(2).strip();
            }
            String description = String.join(" ", desc);
            if (name == null || name.isBlank()) {
                name = "Query " + (out.size() + 1) + " (line " + startLine + ")";
            } else if (name.length() > MAX_TITLE) {
                description = name + (description.isEmpty() ? "" : " " + description);
                name = name.substring(0, MAX_TITLE - 1) + "…";
            }
            out.add(new ReportQuery(name, description, sql, startLine));
        }

        private int indexOrEnd(char ch, int from) {
            int k = s.indexOf(ch, from);
            return k < 0 ? s.length() : k;
        }

        private static int count(String text, char ch) {
            int k = 0;
            for (int j = 0; j < text.length(); j++) {
                if (text.charAt(j) == ch) {
                    k++;
                }
            }
            return k;
        }
    }
}
