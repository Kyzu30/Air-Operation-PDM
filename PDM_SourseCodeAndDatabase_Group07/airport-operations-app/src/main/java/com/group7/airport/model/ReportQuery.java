package com.group7.airport.model;

/** One report loaded from the Task 6 query script (sql/02_AirportOperation_queries.sql). */
public record ReportQuery(String name, String description, String sql) {
    @Override
    public String toString() {
        return name; // shown in the Reports combo box
    }
}
