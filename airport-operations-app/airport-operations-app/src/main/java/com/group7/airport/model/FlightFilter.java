package com.group7.airport.model;

import java.time.LocalDate;

/**
 * Search criteria for flights. Any field may be null/blank = "don't filter on it".
 *
 * @param flightIdKeyword part of the FlightID, e.g. "VN" or "VN812"
 * @param fromCode        departure airport code, e.g. "SGN"
 * @param toCode          arrival airport code
 * @param status          Scheduled / Departed / Arrived / Delayed / Cancelled
 * @param date            flight date
 */
public record FlightFilter(String flightIdKeyword, String fromCode, String toCode,
                           String status, LocalDate date) {

    public static FlightFilter all() {
        return new FlightFilter(null, null, null, null, null);
    }

    public static boolean has(String s) {
        return s != null && !s.isBlank();
    }
}
