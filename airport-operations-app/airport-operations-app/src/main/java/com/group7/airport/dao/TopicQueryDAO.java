package com.group7.airport.dao;

import com.group7.airport.model.QueryResult;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Parameterised versions of the five questions of Topic 7 (Part 3 of the assignment).
 * The application shows Q1-Q5 in the Reports tab, from sql/02_AirportOperation_queries.sql.
 * This class is used by IntegrationTest to check the same questions with other inputs
 * (other flights, dates, routes) and after data changes.
 */
public class TopicQueryDAO {

    /** Q1. The passenger list of a flight (e.g. VN229) on a given date. */
    public static final String Q1 = """
            SELECT t.TicketID, p.PassportID, p.`Name`, t.SeatNumber, t.`Status` AS TicketStatus
            FROM Flight f
            JOIN Ticket t    ON t.FlightID = f.FlightID
            JOIN Passenger p ON p.PassportID = t.PassportID
            WHERE SUBSTRING_INDEX(f.FlightID, '-', 1) = ?
              AND f.`Date` = ?
            ORDER BY p.`Name`
            """;

    /**
     * Q2. Flights delayed by more than N minutes whose actual departure was in the 7 days before a
     * reference date. The range is on ActualDepartureTime itself, so MySQL can use the index
     * idx_flight_actual_departure_time (flights that have not left yet have NULL and drop out).
     */
    public static final String Q2 = """
            SELECT f.FlightID, f.`Date`, f.DepartureAirportCode AS `From`, f.ArrivalAirportCode AS `To`,
                   f.DepartureTime AS ScheduledDeparture, f.ActualDepartureTime,
                   TIMESTAMPDIFF(MINUTE, f.DepartureTime, f.ActualDepartureTime) AS DelayMinutes
            FROM Flight f
            WHERE f.ActualDepartureTime >= ? AND f.ActualDepartureTime < ?
              AND TIMESTAMPDIFF(MINUTE, f.DepartureTime, f.ActualDepartureTime) > ?
            ORDER BY DelayMinutes DESC
            """;

    /** Q3. Total baggage weight carried by each aircraft on a day ("yesterday"). Uses idx_flight_date. */
    public static final String Q3 = """
            SELECT f.AircraftID, a.Model, COUNT(*) AS Bags, SUM(l.Weight) AS TotalWeightKg
            FROM Luggage l
            JOIN Flight f   ON f.FlightID = l.FlightID
            JOIN Aircraft a ON a.AircraftID = f.AircraftID
            WHERE f.`Date` = ? AND f.`Status` <> 'Cancelled'
            GROUP BY f.AircraftID, a.Model
            ORDER BY TotalWeightKg DESC
            """;

    /**
     * Q4. Crew members who worked on EVERY flight of a route in a month.
     * Relational division with a double NOT EXISTS; the first EXISTS stops the answer
     * from being "everyone" when the route has no flight that month.
     */
    public static final String Q4 = """
            SELECT e.EmployeeID, e.`Name`, e.Role
            FROM Employee e
            WHERE EXISTS (SELECT 1 FROM Flight f
                          WHERE f.DepartureAirportCode = ? AND f.ArrivalAirportCode = ?
                            AND f.`Date` >= ? AND f.`Date` < ? AND f.`Status` <> 'Cancelled')
              AND NOT EXISTS (
                  SELECT 1 FROM Flight f
                  WHERE f.DepartureAirportCode = ? AND f.ArrivalAirportCode = ?
                    AND f.`Date` >= ? AND f.`Date` < ? AND f.`Status` <> 'Cancelled'
                    AND NOT EXISTS (SELECT 1 FROM FlightEmployee fe
                                    WHERE fe.FlightID = f.FlightID AND fe.EmployeeID = e.EmployeeID))
            ORDER BY e.Role, e.`Name`
            """;

    /** Q5. Passengers whose baggage was recorded as Loaded but never as Delivered (LuggageEvent history). */
    public static final String Q5 = """
            SELECT p.PassportID, p.`Name`, l.LuggageID, l.FlightID, f.`Status` AS FlightStatus,
                   l.Weight, l.`Status` AS CurrentBagStatus
            FROM Luggage l
            JOIN Passenger p ON p.PassportID = l.PassportID
            JOIN Flight f    ON f.FlightID = l.FlightID
            WHERE EXISTS (SELECT 1 FROM LuggageEvent e
                          WHERE e.PassportID = l.PassportID AND e.LuggageID = l.LuggageID
                            AND e.EventType = 'Loaded')
              AND NOT EXISTS (SELECT 1 FROM LuggageEvent e
                              WHERE e.PassportID = l.PassportID AND e.LuggageID = l.LuggageID
                                AND e.EventType = 'Delivered')
            ORDER BY f.DepartureTime
            """;

    public QueryResult passengerList(String flightNumber, LocalDate date) throws SQLException {
        return QueryRunner.select(Q1, flightNumber, date.toString());
    }

    /** 'yyyy-MM-dd' text compares with DATETIME as midnight of that day: [ref - 7 days, ref). */
    public QueryResult delayedLastWeek(LocalDate referenceDay, int minutes) throws SQLException {
        return QueryRunner.select(Q2, referenceDay.minusDays(7).toString(), referenceDay.toString(), minutes);
    }

    public QueryResult baggagePerAircraft(LocalDate day) throws SQLException {
        return QueryRunner.select(Q3, day.toString());
    }

    public QueryResult crewOnEveryFlight(String from, String to, YearMonth month) throws SQLException {
        String start = month.atDay(1).toString();
        String end = month.plusMonths(1).atDay(1).toString();
        return QueryRunner.select(Q4, from, to, start, end, from, to, start, end);
    }

    public QueryResult loadedNeverDelivered() throws SQLException {
        return QueryRunner.select(Q5);
    }
}
