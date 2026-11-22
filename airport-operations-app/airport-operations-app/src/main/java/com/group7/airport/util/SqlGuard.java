package com.group7.airport.util;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Accepts exactly one read-only statement (SELECT ... or WITH ... SELECT).
 * Used by the Reports tab, where the SQL text can be edited by the user.
 */
public final class SqlGuard {

    private static final Pattern FORBIDDEN = Pattern.compile(
            "\\bINTO\\s+(OUTFILE|DUMPFILE)\\b|\\bFOR\\s+UPDATE\\b|\\bLOCK\\s+IN\\s+SHARE\\s+MODE\\b",
            Pattern.CASE_INSENSITIVE);

    private SqlGuard() { }

    /**
     * @return the statement without comments and without the trailing ';'
     * @throws IllegalArgumentException if it is empty, has several statements, or is not a SELECT/WITH
     */
    public static String requireReadOnly(String sql) {
        String s = stripComments(sql == null ? "" : sql).trim();
        while (s.endsWith(";")) {
            s = s.substring(0, s.length() - 1).trim();
        }
        if (s.isEmpty()) {
            throw new IllegalArgumentException("The query is empty.");
        }
        if (containsTopLevelSemicolon(s)) {
            throw new IllegalArgumentException("Only one statement can be run at a time.");
        }
        String first = s.split("\\s+", 2)[0].toUpperCase(Locale.ROOT);
        if (!first.equals("SELECT") && !first.equals("WITH") && !first.startsWith("(")) {
            throw new IllegalArgumentException("Only SELECT / WITH queries are allowed here (got " + first + ").");
        }
        if (FORBIDDEN.matcher(s).find()) {
            throw new IllegalArgumentException("INTO OUTFILE / FOR UPDATE are not allowed here.");
        }
        return s;
    }

    /* Removes -- , # and /* *&#47; comments while respecting quoted text. */
    static String stripComments(String sql) {
        StringBuilder out = new StringBuilder(sql.length());
        int i = 0;
        int n = sql.length();
        while (i < n) {
            char c = sql.charAt(i);
            if (c == '\'' || c == '"' || c == '`') {
                int j = i + 1;
                while (j < n && sql.charAt(j) != c) {
                    if (sql.charAt(j) == '\\') j++;
                    j++;
                }
                out.append(sql, i, Math.min(j + 1, n));
                i = j + 1;
            } else if (c == '-' && i + 1 < n && sql.charAt(i + 1) == '-'
                       && (i + 2 >= n || Character.isWhitespace(sql.charAt(i + 2)))) {
                while (i < n && sql.charAt(i) != '\n') i++;
            } else if (c == '#') {
                while (i < n && sql.charAt(i) != '\n') i++;
            } else if (c == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
                int end = sql.indexOf("*/", i + 2);
                i = end < 0 ? n : end + 2;
                out.append(' ');
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static boolean containsTopLevelSemicolon(String s) {
        char quote = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quote != 0) {
                if (c == '\\') i++;
                else if (c == quote) quote = 0;
            } else if (c == '\'' || c == '"' || c == '`') {
                quote = c;
            } else if (c == ';') {
                return true;
            }
        }
        return false;
    }
}
