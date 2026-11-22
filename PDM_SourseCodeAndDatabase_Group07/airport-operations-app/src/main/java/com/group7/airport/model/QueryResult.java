package com.group7.airport.model;

import java.util.List;

/**
 * Result of a SELECT: column labels + rows, how long it took, and the SQL text that produced it
 * (shown by the "Show SQL" button, so we can explain the statement behind every screen).
 * Used by every screen so all tables look and export the same way.
 */
public record QueryResult(List<String> columns, List<Object[]> rows, long elapsedMs, String sql) {

    public int rowCount() {
        return rows.size();
    }

    public Object value(int row, String column) {
        int idx = columns.indexOf(column);
        if (idx < 0) {
            throw new IllegalArgumentException("No column " + column + " in " + columns);
        }
        return rows.get(row)[idx];
    }
}
