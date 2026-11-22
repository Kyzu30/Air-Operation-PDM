package com.group7.airport.util;

import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns MySQL error codes into plain-language messages.
 * A constraint violation never reaches the user as a stack trace.
 */
public final class SqlErrors {

    private static final Pattern QUOTED = Pattern.compile("'([^']*)'");
    private static final Pattern FK_TABLE = Pattern.compile("`[^`]+`\\.`([^`]+)`");
    private static final Pattern CHECK_NAME = Pattern.compile("Check constraint '([^']+)'");

    private SqlErrors() { }

    public static String friendly(SQLException e) {
        String msg = e.getMessage() == null ? "" : e.getMessage();
        switch (e.getErrorCode()) {
            case 1062: // duplicate key
                return "This record already exists: the value " + firstQuoted(msg)
                        + " is already used (same ID, or a seat already sold on this flight).";
            case 1451: // parent row still referenced
                return "This record cannot be deleted or changed because other records still refer to it"
                        + tableHint(msg, " (in table ", ")") + ".\nRemove or change those records first.";
            case 1452: // child row points to a missing parent
                return "The value you chose does not exist in the related table"
                        + tableHint(msg, " (checked by table ", ")") + ". Pick an existing value.";
            case 3819: { // CHECK constraint
                Matcher m = CHECK_NAME.matcher(msg);
                return "A value breaks a database rule" + (m.find() ? " (" + m.group(1) + ")" : "") + ".\n"
                        + "For example: weight and capacity must be positive, arrival must be after departure,"
                        + " departure and arrival airports must differ.";
            }
            case 1146: // table or view does not exist (e.g. a view of the Task 6 script not created yet)
                return "Table or view " + firstQuoted(msg) + " does not exist.\n"
                        + "If it is a view from the Task 6 query script, run "
                        + ReportQueryLoader.DEFAULT_FILE + " once in MySQL Workbench to create it.";
            case 1054:
                return "Unknown column " + firstQuoted(msg) + ". Check the column names in the query.";
            case 1064:
                return "SQL syntax error:\n" + msg;
            case 1048:
                return "A required field is empty: " + firstQuoted(msg) + ".";
            case 1406:
                return "A value is too long for field " + firstQuoted(msg) + ".";
            case 1292:
            case 1366:
                return "A value has the wrong format (date or number): " + firstQuoted(msg) + ".";
            case 1792:
                return "This screen is read-only.";
            default:
                return msg; // connection problems are already explained by DBConnection
        }
    }

    private static String firstQuoted(String msg) {
        Matcher m = QUOTED.matcher(msg);
        return m.find() ? "'" + m.group(1) + "'" : "(see details)";
    }

    private static String tableHint(String msg, String before, String after) {
        Matcher m = FK_TABLE.matcher(msg);
        return m.find() ? before + m.group(1) + after : "";
    }
}
