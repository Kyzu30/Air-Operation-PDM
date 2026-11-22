-- =====================================================================
-- Task 6 - SQL query script (commented)
--
-- Run AFTER sql/00_AirportOperation_dataset.sql.
-- Each query is preceded by the question it answers, in English.
-- The Java app (Reports tab) reads this file directly: the first comment
-- line above a query is its title, the next lines are its description.
-- Keep Q1-Q5 (the five Topic 7 questions) as the first five queries:
-- they are the only place where the app answers the topic questions.
-- =====================================================================

USE airport_operations;

-- BASIC AND ADVANCED QUERIES

-- Q1. Passenger list of flight VN229 on a given date (2026-11-16)
-- JOIN connects Passenger, Ticket and Flight; WHERE filters the flight number and the date.
-- FlightID = flight number + '-MMDD', so LIKE 'VN229%' matches VN229 on any day.
SELECT
    P.PassportID,
    P.Name,
    P.Email,
    T.SeatNumber,
    T.Status
FROM Passenger P
JOIN Ticket T
    ON P.PassportID = T.PassportID
JOIN Flight F
    ON T.FlightID = F.FlightID
WHERE F.FlightID LIKE 'VN229%'
  AND F.Date = '2026-11-16';

-- Q2. The flights delayed by more than 30 minutes last week (2026-11-09 to 2026-11-15)
-- Delay = ActualDepartureTime - DepartureTime (scheduled), computed with TIMESTAMPDIFF.
-- Flights that have not departed yet (ActualDepartureTime IS NULL) are excluded.
SELECT
    FlightID,
    `Date`,
    DepartureTime AS ScheduledDeparture,
    ActualDepartureTime,
    TIMESTAMPDIFF(
        MINUTE,
        DepartureTime,
        ActualDepartureTime
    ) AS DelayMinutes
FROM Flight
WHERE `Date` >= '2026-11-09'
  AND `Date` < '2026-11-16'
  AND ActualDepartureTime IS NOT NULL
  AND TIMESTAMPDIFF(
        MINUTE,
        DepartureTime,
        ActualDepartureTime
      ) > 30
ORDER BY `Date`;

-- NESTED QUERIES AND AGGREGATE FUNCTIONS

-- Q3. Total baggage weight carried by each aircraft yesterday (2026-11-18)
-- JOIN connects Aircraft, Flight and Luggage; SUM(L.Weight) adds the bag weights;
-- GROUP BY gives one row per aircraft.
SELECT
    A.AircraftID,
    A.Model,
    SUM(L.Weight) AS TotalBaggageWeight
FROM Aircraft A
JOIN Flight F
    ON A.AircraftID = F.AircraftID
JOIN Luggage L
    ON F.FlightID = L.FlightID
WHERE F.Date = '2026-11-18'
GROUP BY
    A.AircraftID,
    A.Model;

-- Q4. Crew members who worked on every flight on a given route (SGN -> HKG) in a month (November 2026)
-- Relational division with a double NOT EXISTS: keep an employee when there is NO flight
-- on the route that month to which he/she was NOT assigned.
-- The first EXISTS makes sure the route has at least one flight that month
-- (otherwise every employee would satisfy the "for all" condition).
SELECT
    e.EmployeeID,
    e.Name,
    e.Role
FROM Employee e
WHERE EXISTS (
    SELECT 1
    FROM Flight f
    WHERE f.DepartureAirportCode = 'SGN'
      AND f.ArrivalAirportCode = 'HKG'
      AND f.Date >= '2026-11-01'
      AND f.Date < '2026-12-01'
)
AND NOT EXISTS (
    SELECT 1
    FROM Flight f
    WHERE f.DepartureAirportCode = 'SGN'
      AND f.ArrivalAirportCode = 'HKG'
      AND f.Date >= '2026-11-01'
      AND f.Date < '2026-12-01'
      AND NOT EXISTS (
          SELECT 1
          FROM FlightEmployee fe
          WHERE fe.FlightID = f.FlightID
            AND fe.EmployeeID = e.EmployeeID
      )
);

-- Q5. Passengers whose baggage was loaded but never delivered
-- Uses the LuggageEvent history, not the current status: EXISTS finds a 'Loaded' event
-- and NOT EXISTS checks that the same bag has no 'Delivered' event.
SELECT
    P.PassportID,
    P.Name,
    L.LuggageID,
    L.Weight,
    L.FlightID,
    L.Status
FROM Passenger P
JOIN Luggage L
    ON P.PassportID = L.PassportID
WHERE EXISTS (
    SELECT 1
    FROM LuggageEvent LE
    WHERE LE.PassportID = L.PassportID
      AND LE.LuggageID = L.LuggageID
      AND LE.EventType = 'Loaded'
)
AND NOT EXISTS (
    SELECT 1
    FROM LuggageEvent LE
    WHERE LE.PassportID = L.PassportID
      AND LE.LuggageID = L.LuggageID
      AND LE.EventType = 'Delivered'
);
