package com.group7.airport.dao;

import com.group7.airport.db.DBConnection;
import com.group7.airport.model.QueryResult;
import com.group7.airport.util.SqlGuard;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Runs the queries of the Reports tab (as written in the script, or after the user edits them).
 * Two safety layers: SqlGuard only accepts one SELECT/WITH statement,
 * and the connection is switched to read-only before running it.
 */
public class ReportDAO {

    public QueryResult run(String sql) throws SQLException {
        String clean = SqlGuard.requireReadOnly(sql);
        try (Connection conn = DBConnection.get()) {
            conn.setReadOnly(true);
            return QueryRunner.select(conn, clean);
        }
    }
}
