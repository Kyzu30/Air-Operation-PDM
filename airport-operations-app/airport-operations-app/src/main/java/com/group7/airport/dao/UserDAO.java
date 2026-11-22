package com.group7.airport.dao;

import com.group7.airport.db.DBConnection;
import com.group7.airport.util.PasswordUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/* Login against the AppUser table. */
public class UserDAO {

    private static final String FIND_HASH =
            "SELECT PasswordHash FROM AppUser WHERE Username = ?";

    /**
     * @return true only if the username exists and the password hash matches.
     *         Input is bound with '?', so "' OR '1'='1" is just a wrong username.
     */
    public boolean authenticate(String username, char[] password) throws SQLException {
        if (username == null || username.isBlank() || password == null || password.length == 0) {
            return false;
        }
        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(FIND_HASH)) {
            ps.setString(1, username.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return false;
                }
                return PasswordUtil.matches(password, rs.getString("PasswordHash"));
            }
        }
    }

    /* True if the AppUser table exists (used for a clear error message on the login screen). */
    public boolean appUserTableExists() throws SQLException {
        String sql = "SELECT COUNT(*) FROM information_schema.TABLES "
                   + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'AppUser'";
        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() && rs.getInt(1) > 0;
        }
    }
}
