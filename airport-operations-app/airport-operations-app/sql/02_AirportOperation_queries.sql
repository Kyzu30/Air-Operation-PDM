-- Task 6 --

-- @name: Test 1 – Tickets per flight
-- @desc: Each flight with its route, status and number of tickets
SELECT f.FlightID,
       f.`Date`,
       dep.City            AS FromCity,
       arr.City            AS ToCity,
       f.`Status`,
       COUNT(t.TicketID)   AS NumTickets
FROM Flight f
JOIN Airport dep ON dep.AirportCode = f.DepartureAirportCode
JOIN Airport arr ON arr.AirportCode = f.ArrivalAirportCode
LEFT JOIN Ticket t ON t.FlightID = f.FlightID
GROUP BY f.FlightID, f.`Date`, dep.City, arr.City, f.`Status`
ORDER BY NumTickets DESC;