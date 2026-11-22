package com.group7.airport.dao;

import com.group7.airport.model.QueryResult;

import java.sql.SQLException;
import java.util.List;

/* Passenger search, the tickets and luggage of one passenger, and insert / update / delete. */
public class PassengerDAO {

    /* Search by part of the passport ID or the name (Vietnamese names work: utf8mb4). */
    public QueryResult search(String keyword) throws SQLException {
        String sql = """
                SELECT p.PassportID,
                       p.`Name`,
                       p.Email,
                       (SELECT GROUP_CONCAT(ph.PhoneNumber ORDER BY ph.PhoneNumber SEPARATOR ', ')
                          FROM PassengerPhone ph WHERE ph.PassportID = p.PassportID) AS Phones,
                       (SELECT COUNT(*) FROM Ticket t WHERE t.PassportID = p.PassportID) AS Tickets
                FROM Passenger p
                WHERE p.PassportID LIKE ? OR p.`Name` LIKE ?
                ORDER BY p.`Name`
                """;
        String like = "%" + (keyword == null ? "" : keyword.trim()) + "%";
        return QueryRunner.select(sql, like, like);
    }

    public QueryResult tickets(String passportId) throws SQLException {
        String sql = """
                SELECT t.TicketID, t.FlightID,
                       CONCAT(f.DepartureAirportCode, ' -> ', f.ArrivalAirportCode) AS Route,
                       f.DepartureTime, t.SeatNumber,
                       t.`Status` AS TicketStatus, f.`Status` AS FlightStatus, t.TicketPurchaseTime
                FROM Ticket t
                JOIN Flight f ON f.FlightID = t.FlightID
                WHERE t.PassportID = ?
                ORDER BY f.DepartureTime
                """;
        return QueryRunner.select(sql, passportId);
    }

    public QueryResult luggage(String passportId) throws SQLException {
        String sql = """
                SELECT l.LuggageID, l.FlightID, l.Weight, l.`Status`,
                       (SELECT GROUP_CONCAT(CONCAT(sg.GateID, ':', sg.`Result`) SEPARATOR ', ')
                          FROM LuggageSecurityGate lsg
                          JOIN SecurityGate sg ON sg.GateID = lsg.SecurityGateID
                         WHERE lsg.PassportID = l.PassportID AND lsg.LuggageID = l.LuggageID) AS Screening,
                       (SELECT GROUP_CONCAT(CONCAT(e.EventType, ' ', DATE_FORMAT(e.EventTime, '%d/%m %H:%i'))
                                            ORDER BY e.EventTime SEPARATOR ' > ')
                          FROM LuggageEvent e
                         WHERE e.PassportID = l.PassportID AND e.LuggageID = l.LuggageID) AS History
                FROM Luggage l
                WHERE l.PassportID = ?
                ORDER BY l.LuggageID
                """;
        return QueryRunner.select(sql, passportId);
    }

    // ------------------------------------------------------------------ editing

    public static final String INSERT_PASSENGER =
            "INSERT INTO Passenger (PassportID, `Name`, Email) VALUES (?, ?, ?)";
    public static final String UPDATE_PASSENGER =
            "UPDATE Passenger SET `Name` = ?, Email = ? WHERE PassportID = ?";
    public static final String DELETE_PHONES =
            "DELETE FROM PassengerPhone WHERE PassportID = ?";
    public static final String INSERT_PHONE =
            "INSERT INTO PassengerPhone (PassportID, PhoneNumber) VALUES (?, ?)";
    public static final String DELETE_PASSENGER =
            "DELETE FROM Passenger WHERE PassportID = ?";

    /* Passenger + phones written together: one transaction, COMMIT or ROLLBACK. */
    public void insert(String passportId, String name, String email, List<String> phones) throws SQLException {
        QueryRunner.inTransaction(conn -> {
            QueryRunner.update(conn, INSERT_PASSENGER, passportId, name, email);
            for (String ph : phones) {
                QueryRunner.update(conn, INSERT_PHONE, passportId, ph);
            }
        });
    }

    /** Name/email changed and the phone list replaced, in one transaction. The key is not changed. */
    public void update(String passportId, String name, String email, List<String> phones) throws SQLException {
        QueryRunner.inTransaction(conn -> {
            if (QueryRunner.update(conn, UPDATE_PASSENGER, name, email, passportId) == 0) {
                throw new SQLException("Passenger " + passportId + " no longer exists.");
            }
            QueryRunner.update(conn, DELETE_PHONES, passportId);
            for (String ph : phones) {
                QueryRunner.update(conn, INSERT_PHONE, passportId, ph);
            }
        });
    }

    /**
     * Phones go with the passenger (ON DELETE CASCADE). A passenger who still has tickets
     * or luggage is protected by the foreign keys: MySQL refuses (error 1451) and the
     * user gets a plain-language message.
     */
    public int delete(String passportId) throws SQLException {
        return QueryRunner.update(DELETE_PASSENGER, passportId);
    }
}
