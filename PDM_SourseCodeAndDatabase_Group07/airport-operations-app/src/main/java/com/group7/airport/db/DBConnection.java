package com.group7.airport.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Single place that opens JDBC connections.
 * Every DAO call opens a short-lived connection inside try-with-resources,
 * so connections are always closed even when a query fails.
 */
public final class DBConnection {

    private static DBConfig config;

    private DBConnection() { }

    public static synchronized DBConfig config() {
        if (config == null) {
            config = DBConfig.load();
        }
        return config;
    }

    public static Connection get() throws SQLException {
        DBConfig c = config();
        try {
            return DriverManager.getConnection(c.url(), c.user(), c.password());
        } catch (SQLException e) {
            throw new SQLException(explain(e), e.getSQLState(), e.getErrorCode(), e);
        }
    }

    /** Turns common JDBC errors into a hint a teammate can act on. */
    private static String explain(SQLException e) {
        String msg = e.getMessage() == null ? "" : e.getMessage();
        if (msg.contains("No suitable driver")) {
            return "MySQL JDBC driver not found. Add mysql-connector-j to the project (Maven does this automatically).";
        }
        if (e.getErrorCode() == 1045) {
            return "Access denied: wrong MySQL user/password. Check db.properties.";
        }
        if (e.getErrorCode() == 1049) {
            return "Database 'airport_operations' does not exist. Run sql/00_AirportOperation_dataset.sql first.";
        }
        if (msg.contains("Communications link failure")) {
            return "Cannot reach MySQL server. Is MySQL running on the host/port in db.properties?";
        }
        return msg;
    }
}
