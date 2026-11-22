# Airport Operations – Java Desktop Application

**IT079IU – Principles of Database Management · Group 7 · Topic 7: Airport Operations**
International University – VNU-HCM · Semester 1, 2026–2027

A Java Swing desktop application that connects through JDBC to the group's MySQL database
`airport_operations`. A user who knows no SQL can log in, search flights and passengers, browse
every table, insert / update / delete records, answer the five Topic 7 questions and run the group's
report queries. Every screen is driven by SQL written against our own schema, and the SQL behind any
table can be displayed with one click (**Show SQL**).

---

## Contents

1. [Requirements](#1-requirements)
2. [Project structure](#2-project-structure)
3. [Setup and first run](#3-setup-and-first-run)
4. [Configuration reference](#4-configuration-reference)
5. [Using the application](#5-using-the-application)
6. [The Reports tab (Task 6 queries)](#6-the-reports-tab-task-6-queries)
7. [The Topic 7 queries](#7-the-topic-7-queries)
8. [Business rules, validation and error handling](#8-business-rules-validation-and-error-handling)
9. [Integration test](#9-integration-test)
10. [Assignment requirement checklist (§2.4)](#10-assignment-requirement-checklist-24)
11. [About the dataset](#11-about-the-dataset)
12. [Troubleshooting](#12-troubleshooting)
13. [Team](#13-team)

---

## 1. Requirements

| Component | Version | Notes |
|---|---|---|
| JDK | **17 or later** (the build targets Java 17 via `maven.compiler.release`) | Text blocks and records are used |
| Database | **MySQL 8.0 or later** (InnoDB, `utf8mb4_unicode_ci`) | MySQL ≥ 8.0.16 is needed so that `CHECK` constraints are enforced |
| JDBC driver | **MySQL Connector/J 9.1.0** (`com.mysql:mysql-connector-j`) | Downloaded automatically by Maven |
| Build tool | Maven 3.8+ (bundled with NetBeans) | An offline alternative without Maven is described in §3.3 |
| GUI | Java Swing (part of the JDK) | No extra library |
| Tools used by the team | MySQL Workbench, Apache NetBeans / VS Code | Any MySQL client works |

---

## 2. Project structure

```
airport-operations-app/
├── pom.xml                         Maven build (driver dependency, runnable jar)
├── db.properties.example           template for the connection settings
├── db.properties                   YOUR settings (git-ignored, never committed)
├── .gitignore
├── sql/
│   ├── 00_AirportOperation_dataset.sql   schema + sample data (run first; starts with DROP DATABASE)
│   ├── 01_appuser_setup.sql              AppUser table + demo accounts for the login screen
│   └── 02_AirportOperation_queries.sql   Task 6 query script (the 12 queries) – read by the Reports tab
└── src/main/java/com/group7/airport/
    ├── App.java                    entry point (opens the login window)
    ├── db/       DBConfig          loads settings from db.properties / -Ddb.config
    │             DBConnection      opens connections, turns JDBC errors into hints
    ├── dao/      QueryRunner       select / update / inTransaction – every statement is a PreparedStatement
    │             UserDAO           login (AppUser)
    │             FlightDAO         flight search, manifest, crew, baggage, insert / update / delete
    │             PassengerDAO      passenger search, tickets, luggage, insert / update / delete
    │             LuggageDAO        luggage insert / update / delete (+ LuggageEvent, in transactions)
    │             BrowseDAO         any table from a fixed whitelist
    │             TopicQueryDAO     the five Topic 7 queries (parameterised)
    │             ReportDAO         runs a report on a read-only connection
    ├── model/    QueryResult, FlightFilter, ReportQuery
    ├── util/     ReportQueryLoader reads the Task 6 script into reports
    │             SqlGuard          accepts exactly one SELECT / WITH statement
    │             SqlErrors         MySQL error code → plain-language message
    │             Validators        input checks before the database
    │             PasswordUtil      SHA-256, identical to MySQL SHA2(…, 256)
    │             CsvExporter       UTF-8 CSV with BOM (Vietnamese text opens correctly in Excel)
    ├── ui/       LoginFrame, MainFrame, FlightSearchPanel, PassengerSearchPanel, BrowsePanel,
    │             ManageDataPanel (+ EditorPanel, PassengerEditorPanel, FlightEditorPanel, LuggageEditorPanel),
    │             TopicQueriesPanel, ReportsPanel,
    │             ResultTablePanel (shared grid + row count + Export CSV + Show SQL),
    │             UiTask (runs SQL off the UI thread), UiTheme (font size)
    └── testing/  IntegrationTest   end-to-end test, writes test-results/
```

---

## 3. Setup and first run

### 3.1 Create the database (MySQL Workbench)

Run the three scripts **in this order**, each in its own SQL tab (open the file → ⚡ *Execute*):

| # | Script | What it does |
|---|---|---|
| 1 | `sql/00_AirportOperation_dataset.sql` | Drops and recreates `airport_operations`, creates the 15 tables with all constraints and indexes, inserts the sample data. Safe to run again at any time. |
| 2 | `sql/01_appuser_setup.sql` | Creates the `AppUser` table with two demo accounts: `admin` / `admin123` and `staff` / `staff123`. |
| 3 | `sql/02_AirportOperation_queries.sql` | The Task 6 query script. Running it once creates the view(s) it defines; the app itself only *reads* this file. |

> ⚠️ Script 1 begins with `DROP DATABASE`. **Every time you reload the dataset, run scripts 2 and 3 again.**

### 3.2 Connection settings

Copy `db.properties.example` to **`db.properties`** in the same folder and fill in your MySQL password:

```properties
db.url=jdbc:mysql://localhost:3306/airport_operations?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Ho_Chi_Minh&characterEncoding=UTF-8
db.user=root
db.password=your_password
```

`db.properties` is listed in `.gitignore`, so a password is never pushed to the repository.
A marker can point the app at another server without recompiling (see §4).

### 3.3 Build and run

**NetBeans (recommended).** *File → Open Project* → select the `airport-operations-app` folder
(it is recognised as a Maven project; the driver is downloaded the first time, so an internet
connection is needed once) → right-click the project → **Run**. Log in with `admin` / `admin123`.

**VS Code.** Install *Extension Pack for Java* → *File → Open Folder* → open `App.java` → **Run**.

**Command line** (from the project folder):

```bash
mvn compile exec:java                          # run the application
mvn package                                    # build a runnable jar with the driver inside
java -jar target/airport-operations-app-1.0-jar-with-dependencies.jar
```

Start the jar from the project folder so that `db.properties` and `sql/02_…` are found
(or use the options in §4).

<details>
<summary><b>No internet / Maven not working? Use the Connector/J JAR from Lab 6</b></summary>

1. NetBeans → *New Project → Java with Ant → Java Application* (untick *Create Main Class*).
2. Copy the `com` folder from `src/main/java` into the new project's `src`.
3. Copy the `sql` folder and `db.properties` into the new project's root folder.
4. Right-click **Libraries → Add JAR/Folder** → choose `mysql-connector-j-x.x.x.jar` from Lab 6.
5. Set the main class to `com.group7.airport.App` and run.
</details>

---

## 4. Configuration reference

### `db.properties`

| Key | Default when missing | Meaning |
|---|---|---|
| `db.url` | `jdbc:mysql://localhost:3306/airport_operations?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Ho_Chi_Minh&characterEncoding=UTF-8` | JDBC URL |
| `db.user` | `root` | MySQL user |
| `db.password` | *(empty)* | MySQL password |

Lookup order: `-Ddb.config=<file>` → `./db.properties` → `db.properties` on the classpath → built-in defaults.
The file actually used is printed at the top of every integration-test log.

### JVM options (all optional)

| Option | Example | Effect |
|---|---|---|
| `-Ddb.config=<path>` | `-Ddb.config=C:/marker/my.properties` | Use another connection file |
| `-Dreports.file=<path>` | `-Dreports.file=sql/task6_final.sql` | Read the Task 6 queries from another file |
| `-Dui.fontSize=<n>` | `-Dui.fontSize=18` | Bigger or smaller interface (default 16) |

In NetBeans: *Project Properties → Run → VM Options*. On the command line:
`java -Dui.fontSize=18 -jar target/airport-operations-app-1.0-jar-with-dependencies.jar`.

---

## 5. Using the application

After login, the main window has one tab per form required by the assignment.
Every grid has sortable columns, the **row count and run time**, **Export CSV** and **Show SQL**.

| Tab | What the user does | SQL behind it |
|---|---|---|
| **Login** | Username + password, checked against `AppUser`. The window shows immediately whether MySQL and `AppUser` are reachable. | `SELECT PasswordHash FROM AppUser WHERE Username = ?`, SHA-256 compared in constant time |
| **Flights** | Filter by any combination of Flight ID (part of), From, To, Status, Date. Click a flight → its **passengers**, **crew** and **baggage** (with each bag's event history). | Dynamic `WHERE` built only from the filled-in fields, 3 joins, `TIMESTAMPDIFF` for the delay, correlated sub-queries, `GROUP_CONCAT … ORDER BY` |
| **Passengers** | Search by part of the passport ID or name (Vietnamese accents work). Click a passenger → **tickets** and **luggage** (security screening result + event history). | `LIKE ?`, `GROUP_CONCAT`, correlated sub-queries |
| **Browse** | Pick any of the 15 tables → all rows + row count. | `SELECT * FROM <table>` – the table name comes from a fixed whitelist, never from typed text |
| **Manage data** | Passengers, Flights and Luggage: **New / Insert / Update / Delete / Refresh**. Clicking a row copies it into the form; Delete always asks for confirmation. | `INSERT` / `UPDATE` / `DELETE` with `?`; two-table changes run as **one transaction** (COMMIT / ROLLBACK) |
| **Topic queries** | The five Topic 7 questions, each with its own input fields. | See §7 |
| **Reports** | The Task 6 queries: pick one → it runs; edit and re-run; **Run all → CSV**. | See §6 |

### Transactions in *Manage data*

| Action | Statements in one transaction |
|---|---|
| Insert / update passenger | `Passenger` row + its `PassengerPhone` rows (phone list replaced on update) |
| Insert luggage | `Luggage` row + its first `LuggageEvent` |
| Update luggage | `Luggage` row (+ a new `LuggageEvent` when the status changed) |
| Delete luggage | its `LuggageEvent` rows, then the bag. If the bag has a security-scan record, the second `DELETE` is refused by the foreign key and the first one is **rolled back** |

---

## 6. The Reports tab (Task 6 queries)

The report screen does not keep its own copy of the SQL. It reads the Task 6 team's commented query
script — the *second, commented script* required by §2.3 — directly from
**`sql/02_AirportOperation_queries.sql`**. Editing that one file changes both the submitted script and
the app. Click **Reload file** to pick up changes while the app is running.

The screen has:

- **left:** the list of reports with a **Find** box. Find searches the titles, comments and SQL text,
  so typing `NOT EXISTS` or `HAVING` finds the query that uses that technique;
- **right:** the question the selected query answers, its SQL (editable) and the result grid.
  Selecting a report runs it immediately. **Ctrl+Enter** re-runs the edited SQL; **Reset SQL**
  restores the original text;
- **Run all → CSV:** runs every report and saves `report_01.csv`, `report_02.csv`, … plus `index.txt`
  (row counts, and any failures) in a folder you choose. These are the results needed for
  section 8 of the final report;
- **status line:** how many reports were loaded, from which file, and how many statements were skipped.

### How the script is read

No special format is needed. Write the script as the assignment asks — each query preceded by the
question it answers in English:

```sql
-- Q3. Total baggage weight carried by each aircraft on 17 Nov 2026
-- (aggregation with GROUP BY and HAVING)
SELECT f.AircraftID, SUM(l.Weight) AS TotalKg
FROM Luggage l JOIN Flight f ON f.FlightID = l.FlightID
WHERE f.`Date` = '2026-11-17'
GROUP BY f.AircraftID
HAVING SUM(l.Weight) > 0;
```

| In the script | In the Reports tab |
|---|---|
| The comment paragraph just above a query (`--`, `#` or `/* … */`) | First line = title, other lines = description |
| `-- @name:` / `-- @desc:` tags | Used as title / description when present |
| `SELECT …`, `WITH … SELECT …`, `(SELECT …) UNION (SELECT …)` | One report each |
| `CREATE [OR REPLACE] VIEW v AS SELECT …` | A report that runs the view's `SELECT`, so it works even before the view exists. The title of a `DROP VIEW IF EXISTS v;` written just before it is kept. |
| `SELECT * FROM v` | Runs against the view, so the view must exist (run script 3 once) |
| `USE`, `SET`, `DROP`, `INSERT` / `UPDATE` / `DELETE`, `START TRANSACTION`, `COMMIT`, `ROLLBACK`, procedures between `DELIMITER` lines | Skipped and counted — the Reports tab never changes data |
| Banner lines (`=====`), a comment after `;` on the same line, `;` inside quotes or comments | Ignored / handled correctly |

### Safety

The SQL text can be edited, so it is protected twice: `SqlGuard` accepts exactly **one** `SELECT` /
`WITH` statement (no `;`-chained statements, no `INTO OUTFILE`, no `FOR UPDATE`), and `ReportDAO` runs it
on a connection switched to **read-only**. Test T17 checks that `DELETE`, `UPDATE`, `DROP` and
`SELECT 1; DROP TABLE …` are all refused.

---

## 7. The Topic 7 queries

The *Topic queries* tab answers the five questions of Topic 7 with user-supplied parameters (`?`).
The defaults in the tab use the dataset's demo values. Expected answers on the unchanged dataset:

| # | Question | Inputs to try | Expected answer | Technique |
|---|---|---|---|---|
| Q1 | Passenger list of a flight on a given date | `JJ713`, `2026-11-14` | 11 passengers | 3-table join; flight number = part of `FlightID` before `-` |
| Q2 | Flights delayed by more than *N* minutes last week | reference `2026-11-18`, `30` | `VN419-1113` (45 min). With `10`: 3 flights (45, 30, 15 min) | `TIMESTAMPDIFF`; range on `ActualDepartureTime` (uses `idx_flight_actual_departure_time`) |
| Q3 | Total baggage weight per aircraft on a day ("yesterday") | `2026-11-17` | `AC005` – 3 bags, 45.3 kg | Join + `GROUP BY` + `SUM`; uses `idx_flight_date`; cancelled flights excluded |
| Q4 | Crew who worked on **every** flight of a route in a month | `SIN` → `SGN`, `2026-11` | `E007` only. `SGN` → `CDG`: 4 crew | Relational division with double `NOT EXISTS`, guarded by `EXISTS` |
| Q5 | Passengers whose baggage was **loaded but never delivered** | – | `LG00079` (loaded, then delayed) and `LG00087` (loaded only) | `EXISTS` + `NOT EXISTS` on the `LuggageEvent` history |

> The Q2 and Q3 date fields default to *today* / *yesterday*. The sample data is a snapshot taken on
> 15 Nov 2026, so type `2026-11-18` (Q2) or `2026-11-17` (Q3) during a demonstration.

---

## 8. Business rules, validation and error handling

### Enforced by the database (declared in `00_AirportOperation_dataset.sql`)

Primary keys, foreign keys with `ON DELETE` / `ON UPDATE` actions, `NOT NULL`, `UNIQUE (FlightID, SeatNumber)`,
and `CHECK` constraints: positive capacity and weight, arrival after departure, departure ≠ arrival airport,
and the allowed values of every status column, `EventType` and gate status.

### Checked by the application before anything is sent (`Validators` + editor panels)

- required fields and maximum lengths;
- formats: passport (6–12 letters/digits), e-mail, phone (8–15 digits, optional `+`),
  Flight ID (`VN260-1112` = airline + number + `-MMDD`), Luggage ID (`LG00099`);
- numeric range: 0 < bag weight ≤ 32 kg;
- dates: `yyyy-MM-dd` / `yyyy-MM-dd HH:mm`, arrival after departure, `Date` = day of departure;
- an actual departure time only for *Departed* / *Arrived* flights (and required for them),
  between 1 h before and 24 h after the scheduled time.

### Checked with a query before saving (rules a `CHECK` cannot express)

- an aircraft is not used by two non-cancelled flights at overlapping times;
- a bag is accepted only for a passenger holding a valid (not cancelled / no-show) ticket on that flight.

### Plain-language error messages (`SqlErrors`)

| MySQL code | Situation | Message shown (summary) |
|---|---|---|
| 1062 | duplicate key / seat already sold | "This record already exists…" |
| 1451 | delete a row still referenced (e.g. a passenger with tickets) | "…cannot be deleted or changed because other records still refer to it (in table …)" |
| 1452 | foreign key to a missing row | "The value you chose does not exist in the related table…" |
| 3819 | `CHECK` constraint broken | "A value breaks a database rule (…)" |
| 1146 | view / table missing | "…does not exist. If it is a view from the Task 6 query script, run it once…" |
| 1054, 1064 | unknown column, syntax error (Reports editor) | the column / MySQL's syntax message |
| 1048, 1406, 1292, 1366 | empty required field, value too long, wrong date/number format | the field concerned |
| 1045, 1049, link failure | wrong password, database missing, MySQL not running | what to fix (from `DBConnection`) |

Input problems appear as a yellow *Please check your input* warning; database refusals as
*The database refused this action* — never as a stack trace.

### Coding rules followed

- **PreparedStatement only:** every value the user types is bound with `?` (`QueryRunner.bind`).
  The only identifier ever placed in SQL text is a table name from `BrowseDAO`'s fixed whitelist.
- **try-with-resources** closes every `Connection`, `PreparedStatement` and `ResultSet`
  (`QueryRunner`, `UserDAO`); connections are short-lived, one per action.
- Dates are sent as `yyyy-MM-dd` text, so JDBC time-zone conversion can never shift a date by one day.
- Database work runs on a background thread (`UiTask`), so the window never freezes.

---

## 9. Integration test

`src/main/java/com/group7/airport/testing/IntegrationTest.java` tests the whole workflow against the
real database. Run it after the setup in §3.1:

- **NetBeans:** right-click the file → **Run File**
- **Command line:** `mvn compile exec:java -Dexec.mainClass=com.group7.airport.testing.IntegrationTest`

| Group | Checks |
|---|---|
| T01–T03 | connection, row count of 11 tables, Vietnamese text round-trip (utf8mb4) |
| T04–T07 | AppUser exists, correct login, wrong password, SQL injection (`' OR '1'='1`, `admin' -- `) |
| T08–T14 | flight search (single and combined filters), master–detail (manifest / crew / baggage), passenger search and details |
| T15–T16.n | the Task 6 script loads; **every report runs**, its result is saved as CSV |
| T17–T18 | write statements are blocked in Reports; Browse loads all tables and rejects unknown names |
| T19–T23 | the five Topic 7 queries return the expected answers (§7) |
| T24 | nine kinds of invalid input are rejected before the database |
| T25–T30 | passenger insert / update / delete, COMMIT, ROLLBACK, duplicate key, FK refusal, cascade of phones |
| T31–T34 | aircraft overlap rule, flight insert → update → delete, FK refusal, database `CHECK` (bypassing the app) |
| T35–T38 | luggage ticket rule, event history written per status change, Q5 follows the changes, ROLLBACK on refused delete |
| T39 | the dataset is unchanged afterwards (every test removes what it wrote) |

Output, ready to paste or screenshot into report section 9:

- `test-results/integration_<time>.txt` – the PASS / FAIL log with details;
- `test-results/<time>/report_NN.csv` – the result of each report query.

All expected numbers assume `sql/00_AirportOperation_dataset.sql` is unchanged. If the dataset is edited,
update the expected values in the test.

---

## 10. Assignment requirement checklist (§2.4)

| Requirement | Where in the app |
|---|---|
| Connection settings held outside the code | `db.properties`, `-Ddb.config=…` (`db/DBConfig`) |
| Browse form with the number of rows | **Browse** tab |
| Search form with a parameterised query | **Flights** and **Passengers** tabs (`FlightDAO.search`, `PassengerDAO.search`) |
| Master–detail form | **Flights** (passengers / crew / baggage), **Passengers** (tickets / luggage) |
| Insert, update, delete with confirmation before delete | **Manage data** tab |
| Report screen with an aggregate query | **Reports** tab (Task 6 script, e.g. the `GROUP BY … HAVING` query); **Topic queries** Q3 |
| At least four of the five topic queries | **Topic queries** tab – all five |
| PreparedStatement for every user value | every DAO; test T07 |
| Constraint violations explained in plain language | `util/SqlErrors` → `UiTask.showError`; tests T27–T38 |
| Input validated first (required, ranges, date order) | `util/Validators` + editor panels; test T24 |
| Connections, statements, result sets closed (try-with-resources) | `dao/QueryRunner`, `dao/UserDAO` |
| Live insert / update / delete shown in the database | **Manage data**, then `SELECT` in MySQL Workbench |
| README with JDK, driver, connection settings, build/run steps | this file (§1, §3, §4) |

---

## 11. About the dataset

`sql/00_AirportOperation_dataset.sql` — MySQL 8, InnoDB, `utf8mb4_unicode_ci`.

| Table | Rows | Table | Rows |
|---|---|---|---|
| Airport | 25 | Ticket | 110 |
| Aircraft | 15 | Luggage | 98 |
| Gate | 15 | LuggageEvent | 113 |
| SecurityGate | 10 | FlightEmployee | 120 |
| Employee | 30 | PassengerSecurityGate | 67 |
| Passenger | 100 | LuggageSecurityGate | 90 |
| Flight | 30 | PassengerPhone, EmployeeContact | multi-valued attributes |

Points that matter for the app:

- **Snapshot time:** statuses are derived from "now" = **15 Nov 2026, 20:25**.
- **Flight times:** `DepartureTime` / `ArrivalTime` are the *scheduled* times. `ActualDepartureTime`
  is `NULL` until the flight has really left; delays are `ActualDepartureTime − DepartureTime`
  (demo delays: 15, 30 and 45 minutes).
- **`LuggageEvent`** stores the history of each bag (`Loaded`, `Delivered`, `Delayed`, …), so Q5 is
  answered from events, not from the current status. The app appends an event whenever a bag's status
  changes.
- **Indexes:** `idx_flight_date` (helps Q3 and date searches) and `idx_flight_actual_departure_time`
  (helps Q2's last-week range).
- Each flight has 1 pilot, 1 co-pilot and 2 flight attendants; aircraft, gates and crew are never
  double-booked; cancelled and no-show tickets have no luggage or security records.
- `AppUser` is not part of the topic schema; it exists only for the login screen (`01_appuser_setup.sql`).

---

## 12. Troubleshooting

| Message | Fix |
|---|---|
| *Access denied: wrong MySQL user/password* | Correct `db.user` / `db.password` in `db.properties` |
| *Database 'airport_operations' does not exist* | Run `sql/00_AirportOperation_dataset.sql` |
| *Connected, but table AppUser is missing* | Run `sql/01_appuser_setup.sql` (again after every dataset reload) |
| *Cannot reach MySQL server* | Start MySQL (Windows: *Services → MySQL80*), check host/port in `db.url` |
| *MySQL JDBC driver not found* | Maven could not download it → use the Lab 6 JAR method (§3.3) |
| Reports: *Task 6 query script not found* | Put the script at `sql/02_AirportOperation_queries.sql`, or start with `-Dreports.file=<path>`, then **Reload file** |
| Reports: *Table or view '…' does not exist* | Run `sql/02_AirportOperation_queries.sql` once to create its views |
| Reports: a query is missing from the list | It is not a `SELECT` / `WITH` / `CREATE VIEW` statement (see the "skipped" count), or its `;` is missing |
| Vietnamese names show as `?` | Keep `characterEncoding=UTF-8` in `db.url`; the database must be `utf8mb4` |
| Old behaviour after editing code | `mvn clean compile` (or *Clean and Build* in NetBeans) |
| Integration test fails on counts | The dataset was changed – reload `00_AirportOperation_dataset.sql` |

---

## 13. Team

Group 7 – Principles of Database Management (IT079IU), instructor Assoc. Prof. Dr. Nguyen Thi Thuy Loan.

Java application (task rows 7–8: architecture, login and search, Java–database connection, query results,
integration testing): **Bùi Văn Lợi** and **Nguyễn Trí Tín**.
Dataset and Task 6 query script: the teams of those task rows.
See the work-allocation table in the final report for the complete list of contributions.
