package com.group7.airport.dao;

import com.group7.airport.model.FlightFilter;
import com.group7.airport.model.QueryResult;
import com.group7.airport.util.Validators;
import com.group7.airport.util.Validators.ValidationException;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/* Flight search, manifest / crew / baggage of one flight, and insert / update / delete. */
public class FlightDAO {

    private static final String BASE_SELECT = """
            SELECT f.FlightID,
                   f.`Date`,
                   f.DepartureTime,
                   f.ActualDepartureTime,
                   TIMESTAMPDIFF(MINUTE, f.DepartureTime, f.ActualDepartureTime) AS DelayMin,
                   f.ArrivalTime,
                   f.`Status`,
                   CONCAT(f.DepartureAirportCode, ' - ', da.City) AS `From`,
                   CONCAT(f.ArrivalAirportCode,   ' - ', aa.City) AS `To`,
                   ac.Model AS Aircraft,
                   f.GateID AS Gate
            FROM Flight f
            JOIN Airport  da ON da.AirportCode = f.DepartureAirportCode
            JOIN Airport  aa ON aa.AirportCode = f.ArrivalAirportCode
            JOIN Aircraft ac ON ac.AircraftID  = f.AircraftID
            """;

    /* Builds the WHERE clause only from the filters the user filled in. */
    public QueryResult search(FlightFilter f) throws SQLException {
        StringBuilder sql = new StringBuilder(BASE_SELECT);
        List<String> where = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        if (FlightFilter.has(f.flightIdKeyword())) {
            where.add("f.FlightID LIKE ?");
            params.add("%" + f.flightIdKeyword().trim() + "%");
        }
        if (FlightFilter.has(f.fromCode())) {
            where.add("f.DepartureAirportCode = ?");
            params.add(f.fromCode().trim().toUpperCase());
        }
        if (FlightFilter.has(f.toCode())) {
            where.add("f.ArrivalAirportCode = ?");
            params.add(f.toCode().trim().toUpperCase());
        }
        if (FlightFilter.has(f.status())) {
            where.add("f.`Status` = ?");
            params.add(f.status());
        }
        if (f.date() != null) {
            // Sent as 'yyyy-MM-dd' text: avoids JDBC time-zone shifting the date by one day
            where.add("f.`Date` = ?");
            params.add(f.date().toString());
        }
        if (!where.isEmpty()) {
            sql.append("WHERE ").append(String.join(" AND ", where)).append('\n');
        }
        sql.append("ORDER BY f.DepartureTime");
        return QueryRunner.select(sql.toString(), params.toArray());
    }

    /* Everyone holding a ticket on one flight, with their bag count. */
    public QueryResult manifest(String flightId) throws SQLException {
        String sql = """
                SELECT t.TicketID, t.SeatNumber, p.PassportID, p.`Name`, t.`Status` AS TicketStatus,
                       (SELECT COUNT(*) FROM Luggage l
                         WHERE l.PassportID = t.PassportID AND l.FlightID = t.FlightID) AS Bags
                FROM Ticket t
                JOIN Passenger p ON p.PassportID = t.PassportID
                WHERE t.FlightID = ?
                ORDER BY t.SeatNumber
                """;
        return QueryRunner.select(sql, flightId);
    }

    /* Crew assigned to one flight. */
    public QueryResult crew(String flightId) throws SQLException {
        String sql = """
                SELECT e.EmployeeID, e.`Name`, e.Role
                FROM FlightEmployee fe
                JOIN Employee e ON e.EmployeeID = fe.EmployeeID
                WHERE fe.FlightID = ?
                ORDER BY FIELD(e.Role, 'Pilot', 'Co-Pilot', 'Flight Attendant'), e.EmployeeID
                """;
        return QueryRunner.select(sql, flightId);
    }

    /* Airport codes for the From/To combo boxes. */
    public List<String> airportCodes() throws SQLException {
        QueryResult r = QueryRunner.select("SELECT AirportCode FROM Airport ORDER BY AirportCode");
        List<String> codes = new ArrayList<>();
        for (Object[] row : r.rows()) {
            codes.add((String) row[0]);
        }
        return codes;
    }

    /* Bags carried on one flight, with their event history. */
    public QueryResult baggage(String flightId) throws SQLException {
        String sql = """
                SELECT l.LuggageID, l.PassportID, p.`Name`, l.Weight, l.`Status`,
                       (SELECT GROUP_CONCAT(CONCAT(e.EventType, ' ', DATE_FORMAT(e.EventTime, '%d/%m %H:%i'))
                                            ORDER BY e.EventTime SEPARATOR ' > ')
                          FROM LuggageEvent e
                         WHERE e.PassportID = l.PassportID AND e.LuggageID = l.LuggageID) AS History
                FROM Luggage l
                JOIN Passenger p ON p.PassportID = l.PassportID
                WHERE l.FlightID = ?
                ORDER BY l.LuggageID
                """;
        return QueryRunner.select(sql, flightId);
    }

    // ------------------------------------------------------------------ 

    public static final String LIST_FOR_EDIT = """
            SELECT FlightID, `Date`, DepartureTime, ActualDepartureTime, ArrivalTime, `Status`,
                   DepartureAirportCode, ArrivalAirportCode, AircraftID, GateID
            FROM Flight
            ORDER BY DepartureTime
            """;

    /* Business rule from the data assumptions (not expressible as a CHECK): no aircraft in two flights at once. */
    public static final String OVERLAP = """
            SELECT FlightID FROM Flight
            WHERE AircraftID = ? AND FlightID <> ? AND `Status` <> 'Cancelled'
              AND DepartureTime < ? AND ArrivalTime > ?
            LIMIT 1
            """;

    public static final String INSERT = """
            INSERT INTO Flight (FlightID, `Date`, DepartureTime, ActualDepartureTime, ArrivalTime, `Status`,
                                DepartureAirportCode, ArrivalAirportCode, AircraftID, GateID)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    public static final String UPDATE = """
            UPDATE Flight SET `Date` = ?, DepartureTime = ?, ActualDepartureTime = ?, ArrivalTime = ?, `Status` = ?,
                              DepartureAirportCode = ?, ArrivalAirportCode = ?, AircraftID = ?, GateID = ?
            WHERE FlightID = ?
            """;

    public static final String DELETE = "DELETE FROM Flight WHERE FlightID = ?";

    /* One flight as typed in the editing form (already validated). */
    public record FlightRow(String flightId, LocalDate date, LocalDateTime departure, LocalDateTime actualDeparture,
                            LocalDateTime arrival, String status, String from, String to,
                            String aircraftId, String gateId) { }

    public QueryResult listForEdit() throws SQLException {
        return QueryRunner.select(LIST_FOR_EDIT);
    }

    public QueryResult aircraftOptions() throws SQLException {
        return QueryRunner.select("SELECT AircraftID, CONCAT(Model, ' (', Capacity, ' seats)') AS Label "
                + "FROM Aircraft ORDER BY AircraftID");
    }

    public QueryResult gateOptions() throws SQLException {
        return QueryRunner.select("SELECT GateID, `Status` AS Label FROM Gate ORDER BY GateID");
    }

    /* Rule checked with a query before saving; throws ValidationException when it is broken. */
    public void checkAircraftFree(FlightRow f) throws SQLException {
        if ("Cancelled".equals(f.status())) {
            return;
        }
        Object clash = QueryRunner.scalar(OVERLAP, f.aircraftId(), f.flightId(),
                Validators.sql(f.arrival()), Validators.sql(f.departure()));
        if (clash != null) {
            throw new ValidationException("Aircraft " + f.aircraftId() + " is already used by flight "
                    + clash + " in that time window.");
        }
    }

    public void insert(FlightRow f) throws SQLException {
        checkAircraftFree(f);
        QueryRunner.update(INSERT, f.flightId(), Validators.sql(f.date()), Validators.sql(f.departure()),
                Validators.sql(f.actualDeparture()), Validators.sql(f.arrival()), f.status(),
                f.from(), f.to(), f.aircraftId(), f.gateId());
    }

    public void update(FlightRow f) throws SQLException {
        checkAircraftFree(f);
        int n = QueryRunner.update(UPDATE, Validators.sql(f.date()), Validators.sql(f.departure()),
                Validators.sql(f.actualDeparture()), Validators.sql(f.arrival()), f.status(),
                f.from(), f.to(), f.aircraftId(), f.gateId(), f.flightId());
        if (n == 0) {
            throw new SQLException("Flight " + f.flightId() + " no longer exists.");
        }
    }

    /* Tickets, luggage or crew still pointing at the flight -> MySQL 1451 -> plain message. */
    public int delete(String flightId) throws SQLException {
        return QueryRunner.update(DELETE, flightId);
    }
}
