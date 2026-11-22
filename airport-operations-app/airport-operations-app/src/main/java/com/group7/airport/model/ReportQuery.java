package com.group7.airport.model;

/**
 * One report of the Reports tab = one read-only statement of the Task 6 query script.
 *
 * @param name        title shown in the list (the question the query answers)
 * @param description the rest of the comment above the query (may be empty)
 * @param sql         the SELECT / WITH statement that is run
 * @param line        line number of the statement in the script (to find it quickly)
 */
public record ReportQuery(String name, String description, String sql, int line) {

    public ReportQuery {
        description = description == null ? "" : description;
    }

    @Override
    public String toString() {
        return name; // shown in the Reports list
    }
}
