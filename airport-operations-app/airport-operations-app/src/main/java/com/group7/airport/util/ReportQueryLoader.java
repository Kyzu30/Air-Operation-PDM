package com.group7.airport.util;

import com.group7.airport.model.ReportQuery;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the Task 6 query script (sql/02_AirportOperation_queries.sql) into reports.
 * No special format is needed: the comment paragraph just above a query gives its title
 * (first line) and description (other lines). "-- @name:" / "-- @desc:" tags are also accepted.
 *
 * <ul>
 *   <li>SELECT / WITH / (SELECT ...) UNION ... -> one report each</li>
 *   <li>CREATE [OR REPLACE] VIEW v AS SELECT ... -> a report running the view's SELECT</li>
 *   <li>USE, SET, DROP, INSERT/UPDATE/DELETE, transactions, DELIMITER blocks -> skipped and counted</li>
 * </ul>
 *
 * Lookup order: -Dreports.file=path -> ./sql/02_AirportOperation_queries.sql
 * -> classpath /queries/02_AirportOperation_queries.sql (the copy that pom.xml packs into the jar).
 */
public final class ReportQueryLoader {

    public static final Path DEFAULT_FILE = Path.of("sql", "02_AirportOperation_queries.sql");
    private static final String CLASSPATH_FILE = "/queries/02_AirportOperation_queries.sql";

    /** What was loaded, from where, and how many statements were not reports. */
    public record Loaded(List<ReportQuery> queries, String source, int skipped) { }

    private static final Pattern BANNER = Pattern.compile("^[=\\-*#~_+ ]*$");
    private static final Pattern DELIMITER = Pattern.compile("^DELIMITER\\s+(\\S+)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CREATE_VIEW = Pattern.compile(
            "^CREATE\\s+(?:OR\\s+REPLACE\\s+)?(?:ALGORITHM\\s*=\\s*\\w+\\s+)?(?:DEFINER\\s*=\\s*\\S+\\s+)?"
            + "(?:SQL\\s+SECURITY\\s+\\w+\\s+)?VIEW\\s+(\\S+)\\s+(?:\\([^)]*\\)\\s+)?AS\\s+(.*)$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private ReportQueryLoader() { }

    /** Kept for existing callers: only the list of reports. */
    public static List<ReportQuery> load() throws IOException {
        return loadWithInfo().queries();
    }

    public static Loaded loadWithInfo() throws IOException {
        List<Path> candidates = new ArrayList<>();
        String custom = System.getProperty("reports.file");
        if (custom != null && !custom.isBlank()) {
            candidates.add(Path.of(custom.trim()));
        }
        candidates.add(DEFAULT_FILE);

        for (Path p : candidates) {
            if (Files.isRegularFile(p)) {
                try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                    return parseWithInfo(r, p.toAbsolutePath().normalize().toString());
                }
            }
        }
        InputStream in = ReportQueryLoader.class.getResourceAsStream(CLASSPATH_FILE);
        if (in == null) {
            return new Loaded(List.of(), "not found (expected " + DEFAULT_FILE + ")", 0);
        }
        try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return parseWithInfo(r, "classpath:" + CLASSPATH_FILE);
        }
    }

    public static List<ReportQuery> parse(Reader reader) throws IOException {
        return parseWithInfo(reader, "(reader)").queries();
    }

    public static Loaded parseWithInfo(Reader reader, String source) throws IOException {
        Parser p = new Parser();
        BufferedReader br = new BufferedReader(reader);
        String line;
        while ((line = br.readLine()) != null) {
            p.line(line);
        }
        p.finish();
        return new Loaded(List.copyOf(p.out), source, p.skipped);
    }

    // ------------------------------------------------------------------ parser

    private static final class Parser {
        final List<ReportQuery> out = new ArrayList<>();
        int skipped;

        String delimiter = ";";
        final StringBuilder sql = new StringBuilder();   // current statement, raw text
        List<String> comments = new ArrayList<>();      // paragraph above the current statement
        List<String> carried = List.of();                // title of a skipped DROP VIEW, for the CREATE VIEW after it
        boolean blankSinceComment;
        boolean headerBlock;                             // inside a /* ... */ written above a statement
        char quote;                                      // inside '...', "..." or `...` (0 = no)
        boolean codeBlock;                               // inside /* ... */ within the SQL

        void line(String line) {
            String t = line.trim();
            boolean noCodeYet = sql.toString().isBlank() && quote == 0 && !codeBlock;

            if (noCodeYet) {
                if (headerBlock) {
                    int end = t.indexOf("*/");
                    addComment(end < 0 ? t : t.substring(0, end));
                    if (end >= 0) {
                        headerBlock = false;
                        String rest = t.substring(end + 2).trim();
                        if (!rest.isEmpty()) {
                            code(rest);
                        }
                    }
                    return;
                }
                if (t.isEmpty()) {
                    blankSinceComment = true;
                    return;
                }
                Matcher d = DELIMITER.matcher(t);
                if (d.matches()) {
                    delimiter = d.group(1);
                    return;
                }
                if (t.startsWith("--") || t.startsWith("#")) {
                    addComment(t.startsWith("#") ? t.substring(1) : t.substring(2));
                    return;
                }
                if (t.startsWith("/*")) {
                    String body = t.substring(2);
                    int end = body.indexOf("*/");
                    addComment(end < 0 ? body : body.substring(0, end));
                    if (end < 0) {
                        headerBlock = true;
                    } else {
                        String rest = body.substring(end + 2).trim();
                        if (!rest.isEmpty()) {
                            code(rest);
                        }
                    }
                    return;
                }
            }
            code(line);
        }

        void addComment(String raw) {
            String c = raw.replaceFirst("^\\s*\\*?", "").trim();   // also drops the leading '*' of /* blocks
            if (blankSinceComment) {
                comments = new ArrayList<>();
                blankSinceComment = false;
            }
            if (c.isEmpty() || BANNER.matcher(c).matches()) {
                return;
            }
            comments.add(c);
        }

        /* Scans one line of SQL; a delimiter outside quotes/comments ends the statement. */
        void code(String line) {
            int i = 0;
            int n = line.length();
            while (i < n) {
                char c = line.charAt(i);
                if (codeBlock) {
                    if (c == '*' && i + 1 < n && line.charAt(i + 1) == '/') {
                        sql.append("*/");
                        codeBlock = false;
                        i += 2;
                    } else {
                        sql.append(c);
                        i++;
                    }
                } else if (quote != 0) {
                    sql.append(c);
                    if (c == '\\' && i + 1 < n) {
                        sql.append(line.charAt(i + 1));
                        i += 2;
                        continue;
                    }
                    if (c == quote) {
                        quote = 0;
                    }
                    i++;
                } else if (c == '\'' || c == '"' || c == '`') {
                    quote = c;
                    sql.append(c);
                    i++;
                } else if ((c == '-' && line.startsWith("-- ", i)) || (c == '-' && line.startsWith("--", i) && i + 2 == n)
                           || c == '#') {
                    if (!sql.toString().isBlank()) {
                        sql.append(line, i, n);          // comment inside a statement: keep it
                    }
                    i = n;                               // a comment after ';' on the same line is dropped
                } else if (c == '/' && i + 1 < n && line.charAt(i + 1) == '*') {
                    codeBlock = true;
                    sql.append("/*");
                    i += 2;
                } else if (line.startsWith(delimiter, i)) {
                    endStatement();
                    i += delimiter.length();
                } else {
                    sql.append(c);
                    i++;
                }
            }
            if (!sql.toString().isBlank()) {
                sql.append('\n');
            }
        }

        void endStatement() {
            String text = sql.toString().strip();
            sql.setLength(0);
            List<String> c = comments.isEmpty() ? carried : comments;
            comments = new ArrayList<>();
            blankSinceComment = false;
            carried = List.of();
            if (text.isEmpty()) {
                return;
            }
            String bare = SqlGuard.stripComments(text).trim();
            String first = bare.isEmpty() ? "" : bare.split("\\s+", 2)[0].toUpperCase(Locale.ROOT);

            if (first.equals("SELECT") || first.equals("WITH") || first.startsWith("(")) {
                add(c, text, null);
                return;
            }
            Matcher v = CREATE_VIEW.matcher(bare);
            if (v.matches()) {
                add(c, v.group(2).strip(), "View " + v.group(1).replace("`", ""));
                return;
            }
            if (first.equals("DROP") && bare.toUpperCase(Locale.ROOT).matches("DROP\\s+VIEW\\b.*")) {
                carried = c;                              // its title belongs to the CREATE VIEW that follows
            }
            skipped++;
        }

        void add(List<String> c, String sqlText, String fallbackTitle) {
            String name = null;
            String desc = null;
            List<String> rest = new ArrayList<>();
            for (String line : c) {
                if (line.startsWith("@name:")) {
                    name = line.substring(6).trim();
                } else if (line.startsWith("@desc:")) {
                    desc = line.substring(6).trim();
                } else {
                    rest.add(line);
                }
            }
            if (name == null && !rest.isEmpty()) {
                name = rest.remove(0);
            }
            if (desc == null) {
                desc = String.join(" ", rest);
            }
            if (name == null) {
                name = fallbackTitle != null ? fallbackTitle : "Query " + (out.size() + 1);
            }
            out.add(new ReportQuery(name, desc, sqlText));
        }

        void finish() {
            if (headerBlock) {
                headerBlock = false;
            }
            if (!sql.toString().isBlank()) {
                endStatement();                           // last statement without ';'
            }
        }
    }
}
