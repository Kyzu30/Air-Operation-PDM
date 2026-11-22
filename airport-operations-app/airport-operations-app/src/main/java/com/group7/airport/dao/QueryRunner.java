package com.group7.airport.dao;

import com.group7.airport.db.DBConnection;
import com.group7.airport.model.QueryResult;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs parameterised SQL. All user input goes through '?' parameters -> no SQL injection.
 * Every Connection / PreparedStatement / ResultSet is closed with try-with-resources.
 */
public final class QueryRunner {

    private QueryRunner() { }

    // SELECT

    public static QueryResult select(String sql, Object... params) throws SQLException {
        try (Connection conn = DBConnection.get()) {
            return select(conn, sql, params);
        }
    }

    public static QueryResult select(Connection conn, String sql, Object... params) throws SQLException {
        long start = System.nanoTime();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                int n = md.getColumnCount();
                List<String> cols = new ArrayList<>(n);
                for (int c = 1; c <= n; c++) {
                    cols.add(md.getColumnLabel(c)); 
                }
                List<Object[]> rows = new ArrayList<>();
                while (rs.next()) {
                    Object[] row = new Object[n];
                    for (int c = 1; c <= n; c++) {
                        row[c - 1] = rs.getObject(c);
                    }
                    rows.add(row);
                }
                long ms = (System.nanoTime() - start) / 1_000_000;
                return new QueryResult(cols, rows, ms, sql);
            }
        }
    }

    /* First column of the first row, or null. */
    public static Object scalar(String sql, Object... params) throws SQLException {
        QueryResult r = select(sql, params);
        return r.rowCount() == 0 ? null : r.rows().get(0)[0];
    }

    // INSERT / UPDATE / DELETE 

    /* One statement on its own (auto-commit). Returns the number of rows changed. */
    public static int update(String sql, Object... params) throws SQLException {
        try (Connection conn = DBConnection.get()) {
            return update(conn, sql, params);
        }
    }

    public static int update(Connection conn, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            return ps.executeUpdate();
        }
    }

    /* Work that must succeed or fail as a whole. */
    @FunctionalInterface
    public interface TxWork {
        void run(Connection conn) throws SQLException;
    }

    /**
     * Runs several statements as ONE transaction:
     * setAutoCommit(false) -> work -> COMMIT, or ROLLBACK if any statement fails.
     */
    public static void inTransaction(TxWork work) throws SQLException {
        try (Connection conn = DBConnection.get()) {
            conn.setAutoCommit(false);
            try {
                work.run(conn);
                conn.commit();
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    private static void bind(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            if (params[i] == null) {
                ps.setNull(i + 1, Types.NULL);
            } else {
                ps.setObject(i + 1, params[i]);
            }
        }
    }
}
