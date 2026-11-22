package com.group7.airport.dao;

import com.group7.airport.model.QueryResult;
import com.group7.airport.util.Validators.ValidationException;

import java.math.BigDecimal;
import java.sql.SQLException;

/**
 * Luggage insert / update / delete. Two tables change together (Luggage + LuggageEvent),
 * so each action is ONE transaction:
 *   insert : INSERT Luggage + its first LuggageEvent
 *   update : UPDATE Luggage (+ a LuggageEvent when the status changed)
 *   delete : DELETE its LuggageEvent rows, then the bag. If the bag already has a security-scan
 *            record, the second DELETE fails (FK) and the first one is ROLLED BACK.
 */
public class LuggageDAO {

    public static final String LIST = """
            SELECT l.LuggageID, l.PassportID, p.`Name`, l.FlightID, l.Weight, l.`Status`,
                   (SELECT MAX(e.EventTime) FROM LuggageEvent e
                     WHERE e.PassportID = l.PassportID AND e.LuggageID = l.LuggageID) AS LastEventTime
            FROM Luggage l
            JOIN Passenger p ON p.PassportID = l.PassportID
            ORDER BY l.LuggageID
            """;

    /* Business rule: a bag is accepted only for a passenger holding a valid ticket on that flight. */
    public static final String TICKET_CHECK = """
            SELECT COUNT(*) FROM Ticket
            WHERE PassportID = ? AND FlightID = ? AND `Status` NOT IN ('Cancelled', 'No-show')
            """;

    public static final String NEXT_ID =
            "SELECT MAX(CAST(SUBSTRING(LuggageID, 3) AS UNSIGNED)) FROM Luggage";

    public static final String INSERT =
            "INSERT INTO Luggage (PassportID, LuggageID, Weight, `Status`, FlightID) VALUES (?, ?, ?, ?, ?)";
    public static final String UPDATE =
            "UPDATE Luggage SET Weight = ?, `Status` = ?, FlightID = ? WHERE PassportID = ? AND LuggageID = ?";
    public static final String INSERT_EVENT =
            "INSERT INTO LuggageEvent (PassportID, LuggageID, EventType, EventTime) VALUES (?, ?, ?, NOW())";
    public static final String DELETE_EVENTS =
            "DELETE FROM LuggageEvent WHERE PassportID = ? AND LuggageID = ?";
    public static final String DELETE =
            "DELETE FROM Luggage WHERE PassportID = ? AND LuggageID = ?";

    public QueryResult list() throws SQLException {
        return QueryRunner.select(LIST);
    }

    public QueryResult flightOptions() throws SQLException {
        return QueryRunner.select("SELECT FlightID, `Status` AS Label FROM Flight ORDER BY DepartureTime");
    }

    /* Next free ID, e.g. LG00099. */
    public String nextId() throws SQLException {
        Object max = QueryRunner.scalar(NEXT_ID);
        long next = max == null ? 1 : ((Number) max).longValue() + 1;
        return String.format("LG%05d", next);
    }

    public void checkTicket(String passportId, String flightId) throws SQLException {
        Number n = (Number) QueryRunner.scalar(TICKET_CHECK, passportId, flightId);
        if (n == null || n.intValue() == 0) {
            throw new ValidationException("Passenger " + passportId + " has no valid ticket on flight " + flightId
                    + ".\nLuggage can only be added for a flight the passenger is booked on.");
        }
    }

    public void insert(String passportId, String luggageId, BigDecimal weight, String status, String flightId)
            throws SQLException {
        checkTicket(passportId, flightId);
        QueryRunner.inTransaction(conn -> {
            QueryRunner.update(conn, INSERT, passportId, luggageId, weight, status, flightId);
            QueryRunner.update(conn, INSERT_EVENT, passportId, luggageId, status);
        });
    }

    /* @return true when the status changed and an event was recorded */
    public boolean update(String passportId, String luggageId, BigDecimal weight, String status, String flightId,
                          String oldStatus) throws SQLException {
        checkTicket(passportId, flightId);
        boolean statusChanged = !status.equals(oldStatus);
        QueryRunner.inTransaction(conn -> {
            if (QueryRunner.update(conn, UPDATE, weight, status, flightId, passportId, luggageId) == 0) {
                throw new SQLException("Luggage " + luggageId + " no longer exists.");
            }
            if (statusChanged) {
                QueryRunner.update(conn, INSERT_EVENT, passportId, luggageId, status);
            }
        });
        return statusChanged;
    }

    public void delete(String passportId, String luggageId) throws SQLException {
        QueryRunner.inTransaction(conn -> {
            QueryRunner.update(conn, DELETE_EVENTS, passportId, luggageId);
            QueryRunner.update(conn, DELETE, passportId, luggageId);
        });
    }
}
