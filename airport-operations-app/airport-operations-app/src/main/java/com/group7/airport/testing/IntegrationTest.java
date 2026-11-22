package com.group7.airport.testing;

import com.group7.airport.dao.BrowseDAO;
import com.group7.airport.dao.FlightDAO;
import com.group7.airport.dao.LuggageDAO;
import com.group7.airport.dao.TopicQueryDAO;
import com.group7.airport.dao.PassengerDAO;
import com.group7.airport.dao.QueryRunner;
import com.group7.airport.dao.ReportDAO;
import com.group7.airport.dao.UserDAO;
import com.group7.airport.db.DBConnection;
import com.group7.airport.model.FlightFilter;
import com.group7.airport.model.QueryResult;
import com.group7.airport.model.ReportQuery;
import com.group7.airport.util.CsvExporter;
import com.group7.airport.ui.FlightEditorPanel;
import com.group7.airport.ui.LuggageEditorPanel;
import com.group7.airport.ui.PassengerEditorPanel;
import com.group7.airport.util.ReportQueryLoader;
import com.group7.airport.util.SqlErrors;
import com.group7.airport.util.Validators;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Integration test of the full Java workflow against the real MySQL database:
 * config -> connection -> data loaded -> login -> searches -> reports -> safety checks.
 *
 * Run: right-click this file -> Run File (NetBeans), or
 *      mvn -q compile exec:java -Dexec.mainClass=com.group7.airport.testing.IntegrationTest
 *
 * Output ("capture results" for the report):
 *   test-results/integration_<time>.txt   - PASS/FAIL log
 *   test-results/<time>/report_NN.csv     - result of every Task 6 report query
 *
 * Expected numbers come from sql/00_AirportOperation_dataset.sql (unchanged data).
 * The report queries are read from the Task 6 script (sql/02_AirportOperation_queries.sql).
 * Every test that writes data removes what it wrote, so the dataset is the same afterwards.
 */
public class IntegrationTest {

    private final List<String> log = new ArrayList<>();
    private int passed;
    private int failed;

    @FunctionalInterface
    interface Check {
        String run() throws Exception; // returns detail text; throw AssertionError to fail
    }

    public static void main(String[] args) throws IOException {
        new IntegrationTest().runAll();
    }

    private void runAll() throws IOException {
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path outDir = Path.of("test-results");
        Path csvDir = outDir.resolve(stamp);

        line("Airport Operations – Java integration test");
        line("Run at : " + LocalDateTime.now().withNano(0));
        line("Config : " + DBConnection.config().source());
        line("DB URL : " + DBConnection.config().shortUrl());
        line("");

        // ---------- 1. Connection ----------
        check("T01 Connect to MySQL and select airport_operations", () -> {
            try (Connection c = DBConnection.get()) {
                QueryResult r = QueryRunner.select(c, "SELECT DATABASE() AS db, VERSION() AS v");
                expectEquals("airport_operations", r.value(0, "db"));
                return "MySQL " + r.value(0, "v");
            }
        });

        // ---------- 2. Data loaded ----------
        Map<String, Integer> expected = new LinkedHashMap<>();
        expected.put("Airport", 25);
        expected.put("Aircraft", 15);
        expected.put("Gate", 15);
        expected.put("SecurityGate", 10);
        expected.put("Employee", 30);
        expected.put("Passenger", 100);
        expected.put("Flight", 30);
        expected.put("Ticket", 110);
        expected.put("Luggage", 98);
        expected.put("LuggageEvent", 113);
        expected.put("FlightEmployee", 120);
        check("T02 Row counts match the dataset", () -> {
            StringBuilder sb = new StringBuilder();
            for (var e : expected.entrySet()) {
                QueryResult r = QueryRunner.select("SELECT COUNT(*) AS n FROM " + e.getKey());
                long n = ((Number) r.value(0, "n")).longValue();
                expectEquals(e.getValue().longValue(), n, e.getKey());
                sb.append(e.getKey()).append('=').append(n).append(' ');
            }
            return sb.toString().trim();
        });

        check("T03 Vietnamese text round-trips (utf8mb4)", () -> {
            QueryResult r = new PassengerDAO().search("B5594418");
            expectEquals(1, r.rowCount());
            expectEquals("Trần Quốc Long", r.value(0, "Name"));
            return "B5594418 -> " + r.value(0, "Name");
        });

        // ---------- 3. Login ----------
        UserDAO users = new UserDAO();
        check("T04 AppUser table exists", () -> {
            expectTrue(users.appUserTableExists(), "AppUser missing – run sql/01_appuser_setup.sql");
            return "ok";
        });
        check("T05 Login admin/admin123 succeeds", () -> {
            expectTrue(users.authenticate("admin", "admin123".toCharArray()), "login failed");
            return "ok";
        });
        check("T06 Wrong password is rejected", () -> {
            expectTrue(!users.authenticate("admin", "wrong".toCharArray()), "wrong password accepted!");
            return "ok";
        });
        check("T07 SQL injection in username is rejected", () -> {
            expectTrue(!users.authenticate("' OR '1'='1", "x".toCharArray()), "injection logged in!");
            expectTrue(!users.authenticate("admin' -- ", "x".toCharArray()), "injection logged in!");
            return "ok";
        });

        // ---------- 4. Flight search ----------
        FlightDAO flights = new FlightDAO();
        check("T08 Flight search: status = Delayed", () -> {
            QueryResult r = flights.search(new FlightFilter(null, null, null, "Delayed", null));
            expectEquals(3, r.rowCount()); // VN783-1115, VJ829-1118, GE958-1121
            return r.rowCount() + " flights";
        });
        check("T09 Flight search: departing SGN", () -> {
            QueryResult r = flights.search(new FlightFilter(null, "SGN", null, null, null));
            expectEquals(8, r.rowCount());
            return r.rowCount() + " flights";
        });
        check("T10 Flight search: ID contains 'VN'", () -> {
            QueryResult r = flights.search(new FlightFilter("VN", null, null, null, null));
            expectEquals(9, r.rowCount());
            return r.rowCount() + " flights";
        });
        check("T11 Flight search: combined filters (SGN, 2026-11-15)", () -> {
            QueryResult r = flights.search(new FlightFilter(null, "SGN", null, null, LocalDate.of(2026, 11, 15)));
            expectEquals(2, r.rowCount()); // CA737-1115, VN812-1115
            return r.rowCount() + " flights";
        });
        check("T11b Flight search: From BKK, To SGN", () -> {
            QueryResult r = flights.search(new FlightFilter(null, "BKK", "SGN", null, null));
            expectEquals(3, r.rowCount()); // TG152-1111, VN783-1115, VJ829-1118
            return r.rowCount() + " flights";
        });
        check("T11c Flight search: ID 'VN' + Delayed + 2026-11-15", () -> {
            QueryResult r = flights.search(new FlightFilter("VN", null, null, "Delayed", LocalDate.of(2026, 11, 15)));
            expectEquals(1, r.rowCount());
            expectEquals("VN783-1115", r.value(0, "FlightID"));
            return "VN783-1115";
        });
        check("T12 Manifest, crew and baggage of JJ713-1114", () -> {
            QueryResult m = flights.manifest("JJ713-1114");
            QueryResult c = flights.crew("JJ713-1114");
            QueryResult b = flights.baggage("JJ713-1114");
            expectEquals(11, m.rowCount());
            expectEquals(4, c.rowCount());
            expectEquals(10, b.rowCount());
            return m.rowCount() + " passengers, " + c.rowCount() + " crew, " + b.rowCount() + " bags";
        });

        // ---------- 5. Passenger search ----------
        PassengerDAO pax = new PassengerDAO();
        check("T13 Passenger search by name 'Nguyễn'", () -> {
            QueryResult r = pax.search("Nguyễn");
            expectEquals(4, r.rowCount());
            return r.rowCount() + " passengers";
        });
        check("T14 Tickets and luggage of C2417239", () -> {
            QueryResult t = pax.tickets("C2417239");
            QueryResult l = pax.luggage("C2417239");
            expectEquals(2, t.rowCount());
            expectEquals(2, l.rowCount());
            return t.rowCount() + " tickets, " + l.rowCount() + " bags";
        });

        // ---------- 6. Reports (Task 6 query script) ----------
        ReportDAO reports = new ReportDAO();
        ReportQueryLoader.Result loaded = loadReports();
        List<ReportQuery> queries = loaded.queries();
        check("T15 Task 6 query script loads", () -> {
            expectTrue(!queries.isEmpty(), loaded.source());
            return queries.size() + " reports from " + loaded.source()
                    + (loaded.skipped() > 0 ? " (" + loaded.skipped() + " non-SELECT statements skipped)" : "");
        });
        int i = 1;
        for (ReportQuery q : queries) {
            int n = i++;
            check(String.format("T16.%d Report runs: %s", n, q.name()), () -> {
                QueryResult r = reports.run(q.sql());
                Path csv = csvDir.resolve(String.format("report_%02d.csv", n));
                CsvExporter.write(r, csv);
                return r.rowCount() + " rows, " + r.elapsedMs() + " ms -> " + csv;
            });
        }

        // ---------- 7. Safety ----------
        check("T17 Reports tab blocks write statements", () -> {
            for (String bad : List.of("DELETE FROM Ticket", "UPDATE Flight SET `Status`='X'",
                                      "SELECT 1; DROP TABLE Ticket", "DROP DATABASE airport_operations")) {
                try {
                    reports.run(bad);
                    throw new AssertionError("was allowed: " + bad);
                } catch (IllegalArgumentException expectedRejection) {
                    // good
                }
            }
            QueryResult r = QueryRunner.select("SELECT COUNT(*) AS n FROM Ticket");
            expectEquals(110L, ((Number) r.value(0, "n")).longValue(), "Ticket");
            return "4 statements blocked, data unchanged";
        });

        // ---------- 8. Browse ----------
        BrowseDAO browse = new BrowseDAO();
        check("T18 Browse loads every table and refuses unknown names", () -> {
            for (String t : BrowseDAO.TABLES) {
                browse.browse(t);
            }
            try {
                browse.browse("Ticket; DROP TABLE Ticket");
                throw new AssertionError("unknown table was accepted");
            } catch (IllegalArgumentException expectedRejection) {
                // good
            }
            return BrowseDAO.TABLES.size() + " tables";
        });

        // ---------- 9. Topic 7 queries ----------
        TopicQueryDAO topic = new TopicQueryDAO();
        check("T19 Q1 passenger list JJ713 on 2026-11-14", () -> {
            QueryResult r = topic.passengerList("JJ713", LocalDate.of(2026, 11, 14));
            expectEquals(11, r.rowCount());
            return r.rowCount() + " passengers";
        });
        check("T20 Q2 delayed > 30 min / > 10 min in week before 2026-11-18", () -> {
            QueryResult r30 = topic.delayedLastWeek(LocalDate.of(2026, 11, 18), 30);
            QueryResult r10 = topic.delayedLastWeek(LocalDate.of(2026, 11, 18), 10);
            expectEquals(1, r30.rowCount());
            expectEquals("VN419-1113", r30.value(0, "FlightID"));
            expectEquals(45L, ((Number) r30.value(0, "DelayMinutes")).longValue(), "delay");
            expectEquals(3, r10.rowCount()); // VN419-1113 (45), QF630-1112 (30), VN260-1112 (15)
            return "VN419-1113 (45 min); 3 flights over 10 min";
        });
        check("T21 Q3 baggage per aircraft on 2026-11-17", () -> {
            QueryResult r = topic.baggagePerAircraft(LocalDate.of(2026, 11, 17));
            expectEquals(1, r.rowCount());
            expectEquals("AC005", r.value(0, "AircraftID"));
            expectEquals(0, new BigDecimal("45.3").compareTo(new BigDecimal(r.value(0, "TotalWeightKg").toString())));
            return "AC005, " + r.value(0, "Bags") + " bags, " + r.value(0, "TotalWeightKg") + " kg";
        });
        check("T22 Q4 crew on every flight SIN->SGN and SGN->CDG, Nov 2026", () -> {
            QueryResult a = topic.crewOnEveryFlight("SIN", "SGN", YearMonth.of(2026, 11));
            QueryResult b = topic.crewOnEveryFlight("SGN", "CDG", YearMonth.of(2026, 11));
            expectEquals(1, a.rowCount());
            expectEquals("E007", a.value(0, "EmployeeID"));
            expectEquals(4, b.rowCount());
            return "SIN->SGN: E007; SGN->CDG: 4 crew";
        });
        check("T23 Q5 loaded but never delivered", () -> {
            QueryResult r = topic.loadedNeverDelivered();
            expectEquals(2, r.rowCount()); // LG00079, LG00087
            return r.rowCount() + " bags";
        });

        // ---------- 10. Input validation (before the database) ----------
        check("T24 Validation rejects bad input", () -> {
            expectInvalid(() -> PassengerEditorPanel.validPassport("ab"));
            expectInvalid(() -> PassengerEditorPanel.validEmail("not-an-email"));
            expectInvalid(() -> PassengerEditorPanel.validPhones("12345"));
            expectInvalid(() -> LuggageEditorPanel.validWeight("40"));
            expectInvalid(() -> LuggageEditorPanel.validWeight("-1"));
            expectInvalid(() -> LuggageEditorPanel.validWeight("abc"));
            expectInvalid(() -> FlightEditorPanel.validate("X1-1125", "2026-11-25", "2026-11-25 10:00", "",
                    "2026-11-25 08:00", "Scheduled", "SGN", "HAN", "AC001", "G01"));       // arrival before departure
            expectInvalid(() -> FlightEditorPanel.validate("X1-1125", "2026-11-25", "2026-11-25 08:00", "",
                    "2026-11-25 10:00", "Scheduled", "SGN", "SGN", "AC001", "G01"));       // same airports
            expectInvalid(() -> FlightEditorPanel.validate("X1-1125", "2026-11-25", "2026-11-25 08:00", "",
                    "2026-11-25 10:00", "Arrived", "SGN", "HAN", "AC001", "G01"));         // Arrived needs actual time
            return "9 bad inputs rejected";
        });

        // ---------- 11. Passenger editing + transaction ----------
        PassengerDAO pe = new PassengerDAO();
        try {
            check("T25 Insert passenger + 2 phones in one transaction", () -> {
                pe.insert("TEST0001", "Test O'Neil", "t@x.com", List.of("+84901234567", "+84907654321"));
                expectEquals(2L, count("SELECT COUNT(*) FROM PassengerPhone WHERE PassportID = 'TEST0001'"), "phones");
                return "committed";
            });
            check("T26 Update passenger and replace phones", () -> {
                pe.update("TEST0001", "Test Updated", null, List.of("+84901234567"));
                expectEquals(1L, count("SELECT COUNT(*) FROM PassengerPhone WHERE PassportID = 'TEST0001'"), "phones");
                return "committed";
            });
            check("T27 ROLLBACK: failing 2nd statement undoes the 1st", () -> {
                expectSqlError(() -> pe.insert("TEST0002", "Rollback", null, List.of("+8411111111", "+8411111111")),
                        1062, "already exists");
                expectEquals(0L, count("SELECT COUNT(*) FROM Passenger WHERE PassportID = 'TEST0002'"), "TEST0002");
                return "passenger row rolled back";
            });
            check("T28 Duplicate key -> plain message", () ->
                    expectSqlError(() -> pe.insert("B5594418", "Dup", null, List.of()), 1062, "already exists"));
            check("T29 Delete passenger with tickets -> refused with plain message", () ->
                    expectSqlError(() -> pe.delete("B5594418"), 1451, "cannot be deleted"));
            check("T30 Delete passenger cascades phones", () -> {
                expectEquals(1, pe.delete("TEST0001"), "rows deleted");
                expectEquals(0L, count("SELECT COUNT(*) FROM PassengerPhone WHERE PassportID = 'TEST0001'"), "phones");
                return "ok";
            });
        } finally {
            cleanup("DELETE FROM Passenger WHERE PassportID IN ('TEST0001', 'TEST0002')");
        }

        // ---------- 12. Flight editing ----------
        try {
            check("T31 Aircraft overlap detected", () -> {
                FlightDAO.FlightRow clash = FlightEditorPanel.validate("VN999-1114", "2026-11-14", "2026-11-14 08:00",
                        "", "2026-11-14 12:00", "Scheduled", "SGN", "HAN", "AC014", "G01");
                expectInvalid(() -> {
                    try {
                        flights.checkAircraftFree(clash);
                    } catch (java.sql.SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
                return "AC014 busy with JJ713-1114";
            });
            check("T32 Flight insert -> update (actual time) -> delete", () -> {
                flights.insert(FlightEditorPanel.validate("VN999-1125", "2026-11-25", "2026-11-25 08:00", "",
                        "2026-11-25 10:00", "Scheduled", "SGN", "HAN", "AC001", "G01"));
                flights.update(FlightEditorPanel.validate("VN999-1125", "2026-11-25", "2026-11-25 08:00",
                        "2026-11-25 08:40", "2026-11-25 10:00", "Departed", "SGN", "HAN", "AC001", "G01"));
                QueryResult r = flights.search(new FlightFilter("VN999", null, null, null, null));
                expectEquals(40L, ((Number) r.value(0, "DelayMin")).longValue(), "delay");
                expectEquals(1, flights.delete("VN999-1125"), "rows deleted");
                return "delay 40 min, then deleted";
            });
            check("T33 Delete flight with tickets -> refused", () ->
                    expectSqlError(() -> flights.delete("JJ713-1114"), 1451, "cannot be deleted"));
            check("T34 DB CHECK refuses arrival before departure (bypassing the app checks)", () ->
                    expectSqlError(() -> QueryRunner.update(FlightDAO.INSERT, "BAD1-1125", "2026-11-25",
                            "2026-11-25 10:00:00", null, "2026-11-25 08:00:00", "Scheduled", "SGN", "HAN",
                            "AC001", "G01"), 3819, "breaks a database rule"));
        } finally {
            cleanup("DELETE FROM Flight WHERE FlightID IN ('VN999-1125', 'BAD1-1125')");
        }

        // ---------- 13. Luggage editing (Luggage + LuggageEvent transactions) ----------
        LuggageDAO lug = new LuggageDAO();
        String[] newId = new String[1];
        try {
            check("T35 Bag refused when the passenger has no ticket on that flight", () -> {
                expectInvalid(() -> {
                    try {
                        lug.checkTicket("B5594418", "VN925-1117");
                    } catch (java.sql.SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
                return "ok";
            });
            check("T36 Insert -> Loaded -> Delivered writes events; Q5 goes 2 -> 3 -> 2", () -> {
                newId[0] = lug.nextId();
                expectEquals("LG00099", newId[0], "next id");
                lug.insert("B5594418", newId[0], new BigDecimal("10.0"), "Checked-in", "JJ713-1114");
                lug.update("B5594418", newId[0], new BigDecimal("10.0"), "Loaded", "JJ713-1114", "Checked-in");
                long q5a = topic.loadedNeverDelivered().rowCount();
                lug.update("B5594418", newId[0], new BigDecimal("10.0"), "Delivered", "JJ713-1114", "Loaded");
                long q5b = topic.loadedNeverDelivered().rowCount();
                long events = count("SELECT COUNT(*) FROM LuggageEvent WHERE LuggageID = '" + newId[0] + "'");
                expectEquals(3L, events, "events");
                expectEquals(3L, q5a, "Q5 after Loaded");
                expectEquals(2L, q5b, "Q5 after Delivered");
                return "3 events; Q5 " + q5a + " -> " + q5b;
            });
            check("T37 Delete bag removes its events too", () -> {
                lug.delete("B5594418", newId[0]);
                expectEquals(0L, count("SELECT COUNT(*) FROM LuggageEvent WHERE LuggageID = '" + newId[0] + "'"), "events");
                return "ok";
            });
            check("T38 Delete scanned bag -> refused and ROLLED BACK (events kept)", () -> {
                expectSqlError(() -> lug.delete("X32711147", "LG00079"), 1451, "cannot be deleted");
                expectEquals(2L, count("SELECT COUNT(*) FROM LuggageEvent WHERE LuggageID = 'LG00079'"), "events");
                return "2 events still there";
            });
        } finally {
            cleanup("DELETE FROM LuggageEvent WHERE LuggageID = 'LG00099'");
            cleanup("DELETE FROM Luggage WHERE LuggageID = 'LG00099'");
        }
        check("T39 Dataset unchanged after the tests", () -> {
            expectEquals(100L, count("SELECT COUNT(*) FROM Passenger"), "Passenger");
            expectEquals(30L, count("SELECT COUNT(*) FROM Flight"), "Flight");
            expectEquals(98L, count("SELECT COUNT(*) FROM Luggage"), "Luggage");
            expectEquals(113L, count("SELECT COUNT(*) FROM LuggageEvent"), "LuggageEvent");
            return "ok";
        });

        // ---------- summary ----------
        line("");
        line(String.format("SUMMARY: %d passed, %d failed", passed, failed));

        Files.createDirectories(outDir);
        Path report = outDir.resolve("integration_" + stamp + ".txt");
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(report, StandardCharsets.UTF_8))) {
            log.forEach(w::println);
        }
        System.out.println("Saved log to " + report.toAbsolutePath());
        if (failed > 0) {
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------ helpers

    /** A missing script is reported by T15 (source = the error text) instead of stopping the whole run. */
    private static ReportQueryLoader.Result loadReports() {
        try {
            return ReportQueryLoader.load();
        } catch (IOException e) {
            return new ReportQueryLoader.Result(e.getMessage(), List.of(), 0);
        }
    }

    private void check(String name, Check c) {
        try {
            String detail = c.run();
            passed++;
            line("[PASS] " + name + "  (" + detail + ")");
        } catch (Throwable t) {
            failed++;
            String msg = t.getMessage() == null ? t.toString() : t.getMessage();
            line("[FAIL] " + name + "  -> " + msg);
        }
    }

    private void line(String s) {
        System.out.println(s);
        log.add(s);
    }

    private static void expectEquals(Object expected, Object actual) {
        expectEquals(expected, actual, "value");
    }

    private static void expectEquals(Object expected, Object actual, String what) {
        if (expected instanceof Integer e && actual instanceof Number a) {
            expected = e.longValue();
            actual = a.longValue();
        }
        if (!expected.equals(actual)) {
            throw new AssertionError(what + ": expected " + expected + " but got " + actual);
        }
    }

    /** Count query with constant SQL (no user input). */
    private static long count(String sql) throws java.sql.SQLException {
        return ((Number) QueryRunner.scalar(sql)).longValue();
    }

    private void cleanup(String sql) {
        try {
            QueryRunner.update(sql);
        } catch (java.sql.SQLException e) {
            line("[WARN] cleanup failed: " + sql + " -> " + e.getMessage());
        }
    }

    @FunctionalInterface
    interface Action {
        void run() throws Exception;
    }

    private static void expectInvalid(Action a) throws Exception {
        try {
            a.run();
        } catch (Validators.ValidationException expected) {
            return;
        }
        throw new AssertionError("bad input was accepted");
    }

    /** The action must fail with this MySQL error code, and SqlErrors must explain it in plain words. */
    private static String expectSqlError(Action a, int code, String friendlyText) throws Exception {
        try {
            a.run();
        } catch (java.sql.SQLException e) {
            String friendly = SqlErrors.friendly(e);
            expectEquals((long) code, (long) e.getErrorCode(), "MySQL error code");
            expectTrue(friendly.contains(friendlyText), "message was: " + friendly);
            return "error " + code + " -> \"" + friendly.lines().findFirst().orElse("") + "\"";
        }
        throw new AssertionError("no error raised");
    }

    private static void expectTrue(boolean cond, String msg) {
        if (!cond) {
            throw new AssertionError(msg);
        }
    }
}
