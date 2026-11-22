package com.group7.airport.dao;

import com.group7.airport.model.QueryResult;

import java.sql.SQLException;
import java.util.List;

public class BrowseDAO {

    public static final List<String> TABLES = List.of(
            "Airport", "Aircraft", "Gate", "Flight", "Passenger", "PassengerPhone", "Ticket",
            "Luggage", "LuggageEvent", "Employee", "EmployeeContact", "FlightEmployee",
            "SecurityGate", "PassengerSecurityGate", "LuggageSecurityGate");

    public QueryResult browse(String table) throws SQLException {
        if (!TABLES.contains(table)) {
            throw new IllegalArgumentException("Unknown table: " + table);
        }
        return QueryRunner.select("SELECT * FROM `" + table + "`");
    }
}
