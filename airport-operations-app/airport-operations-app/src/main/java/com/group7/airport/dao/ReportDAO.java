package com.group7.airport.dao;

import com.group7.airport.db.DBConnection;
import com.group7.airport.model.QueryResult;
import com.group7.airport.util.SqlGuard;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Runs report / free-text queries from the Reports tab.
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
