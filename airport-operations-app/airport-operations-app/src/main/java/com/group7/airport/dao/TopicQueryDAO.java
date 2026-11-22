package com.group7.airport.dao;

import com.group7.airport.model.QueryResult;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * The five questions of Topic 7 (Airport Operations), Part 3 of the assignment.
 * Each one takes its inputs as '?' parameters, so they are reachable from the "Topic queries" tab
 * with values typed by the user. (The Reports tab shows the Task 6 script itself.)
 */
public class TopicQueryDAO {

    /** Q1. The passenger list of a flight on a given date. */
    public static final String Q1 = """

            """;

    /**
     * Q2. Flights delayed by more than N minutes whose actual departure was in the 7 days before a
     * reference date. The range is on ActualDepartureTime itself, so MySQL can use the index
     * idx_flight_actual_departure_time (flights that have not left yet have NULL and drop out).
     */
    public static final String Q2 = """

            """;

    /** Q3. Total baggage weight carried by each aircraft on a day ("yesterday"). Uses idx_flight_date. */
    public static final String Q3 = """

            """;

    /**
     * Q4. Crew members who worked on EVERY flight of a route in a month.
     * Relational division with a double NOT EXISTS; the first EXISTS stops the answer
     * from being "everyone" when the route has no flight that month.
     */
    public static final String Q4 = """

            """;

    /** Q5. Passengers whose baggage was recorded as Loaded but never as Delivered (LuggageEvent history). */
    public static final String Q5 = """

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
