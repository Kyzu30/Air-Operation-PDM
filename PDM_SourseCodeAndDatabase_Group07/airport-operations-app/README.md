# Airport Operations – Java Application

IT079IU Principles of Database Management · Group 7 · Topic 7: Airport Operations  
International University – VNU-HCM · Semester 1, 2026–2027

A Java Swing desktop application that connects to the group's MySQL database
`airport_operations` through JDBC. Every screen reads or writes the database with SQL
written against our own schema.

---

## 1. System requirements

| Item | Version used | Notes |
|---|---|---|
| JDK | **17 or later** | The build targets Java 17 (`maven.compiler.release = 17`). Tested with JDK 17 and 21. |
| Database system | **MySQL 8.0** (tested on 8.0.46) | 8.0.16 or later is needed, because older versions ignore `CHECK` constraints. |
| JDBC driver | **MySQL Connector/J 9.1.0** (`com.mysql:mysql-connector-j:9.1.0`) | Downloaded by Maven on the first build. |
| Build tool | Maven 3.8 or later | Already included in Apache NetBeans. |
| GUI library | Java Swing | Part of the JDK, nothing to install. |

---

## 2. Folder contents

```
airport-operations-app/
├── README.md
├── pom.xml                      Maven build file (driver dependency, runnable jar)
├── db.properties.example        template for the connection settings
├── .gitignore
├── sql/
│   ├── 00_AirportOperation_dataset.sql   schema, constraints, indexes and sample data
│   ├── 01_appuser_setup.sql              AppUser table for the login screen
│   └── 02_AirportOperation_queries.sql   commented query script (shown in the Reports tab)
└── src/main/java/com/group7/airport/
    ├── App.java          entry point
    ├── db/               DBConfig, DBConnection (connection layer)
    ├── dao/              one class per area: flights, passengers, luggage, browse, reports, login,
    │                     and TopicQueryDAO (parameterised Topic 7 queries, used by the integration test)
    ├── model/            small data classes
    ├── util/             validation, MySQL error messages, CSV export, password hashing, script reader
    ├── ui/               the windows and tabs
    └── testing/          IntegrationTest (end-to-end test against the real database)
```

---

## 3. Step 1 – Create the database

Open MySQL Workbench (or any MySQL client) and run the three scripts **in this order**:

| Order | Script | What it does |
|---|---|---|
| 1 | `sql/00_AirportOperation_dataset.sql` | Drops `airport_operations` if it exists, creates it, creates the 15 tables with all keys, constraints and indexes, then inserts the sample data. It can be run again at any time. |
| 2 | `sql/01_appuser_setup.sql` | Creates the `AppUser` table used by the login screen, with the account `admin` / `admin123`. |
| 3 | `sql/02_AirportOperation_queries.sql` | The commented query script. Running it once checks that every query works; the application only reads this file. |

Script 1 starts with `DROP DATABASE`, so **run script 2 again every time you reload script 1**,
otherwise the login screen reports that `AppUser` is missing.

---

## 4. Step 2 – Connection settings

The connection is configured outside the code, so the application can point at another server
without recompiling.

1. Copy `db.properties.example` to a new file named **`db.properties`** in the project folder
   (the same folder as `pom.xml`).
2. Edit the three keys for your own server:

```properties
db.url=jdbc:mysql://localhost:3306/airport_operations?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Ho_Chi_Minh&characterEncoding=UTF-8
db.user=root
db.password=your_mysql_password
```

| Key | Meaning | Value used if the key is missing |
|---|---|---|
| `db.url` | JDBC URL: host, port and database name | the URL shown above (`localhost:3306`) |
| `db.user` | MySQL user name | `root` |
| `db.password` | MySQL password | empty |

The settings file is looked up in this order:

1. the file given with the JVM option `-Ddb.config=<path>`, otherwise `db.properties` in the folder
   the application is started from;
2. `db.properties` on the classpath;
3. the built-in values in the table above.

Keep `characterEncoding=UTF-8` in the URL so that Vietnamese names are shown correctly.

---

## 5. Step 3 – Build and run

An internet connection is needed the first time, so that Maven can download Connector/J.

### Option A – Apache NetBeans (recommended)

1. *File → Open Project* and choose the `airport-operations-app` folder
   (NetBeans recognises it as a Maven project).
2. Right-click the project → **Clean and Build**.
3. Right-click the project → **Run**.
4. Log in with **`admin` / `admin123`**.

### Option B – Command line

Run these commands from the project folder (the folder that contains `pom.xml`):

```bash
mvn clean package
java -jar target/airport-operations-app-1.0-jar-with-dependencies.jar
```

`mvn clean package` builds a single jar that already contains the JDBC driver and a copy of
the query script. Start it from the project folder so that it finds `db.properties`. To start it
from somewhere else, give the settings file:

```bash
java -Ddb.config=C:/path/to/db.properties -jar airport-operations-app-1.0-jar-with-dependencies.jar
```

To run without building a jar: `mvn compile exec:java`

### Optional JVM options

| Option | Effect |
|---|---|
| `-Ddb.config=<path>` | use another connection settings file |
| `-Dreports.file=<path>` | read the query script from another file |
| `-Dui.fontSize=<n>` | change the interface font size (default 16) |

In NetBeans these go in *Project Properties → Run → VM Options*.

---

## 6. The screens

| Tab | What it does | SQL behind it |
|---|---|---|
| Login | Checks the user name and password against `AppUser`. | `SELECT PasswordHash FROM AppUser WHERE Username = ?` |
| Flights | Search form (flight ID, from, to, status, date). Selecting a flight loads its passengers, crew and baggage in the lower grids (master–detail). | `WHERE` built only from the filled-in fields, every value bound with `?` |
| Passengers | Search by passport ID or name. Selecting a passenger loads their tickets and luggage. | `LIKE ?` and joins |
| Browse | Choose any of the 15 tables; all rows are shown with the row count. | `SELECT * FROM <table>`, table name taken from a fixed list |
| Manage data | Insert, update and delete passengers, flights and luggage. Delete asks for confirmation. Changes that touch two tables run in one transaction with `COMMIT` / `ROLLBACK`. | `INSERT` / `UPDATE` / `DELETE` with `?` parameters |
| Reports | The queries of `sql/02_AirportOperation_queries.sql`, including the five Topic 7 questions (Q1–Q5) and the aggregate queries. Choosing a report shows the question it answers, its SQL and its result. | statements read from the script; read-only connection, `SELECT` / `WITH` only |

Every result grid shows the number of rows and has **Show SQL** and **Export CSV** buttons.

### The Reports tab

- **Report** list: the queries of the script, in script order. The comment line above each query
  in the script is its title; the next comment lines are shown as its description.
- **Run (Ctrl+Enter)** runs the SQL in the box. The SQL can be edited (for example, to try another
  date) and **Reset SQL** restores the text from the script. Only one `SELECT` or `WITH` statement
  is accepted, and it runs on a read-only connection, so this tab can never change data.
- **Reload file** reads the script again, so changes to `sql/02_AirportOperation_queries.sql`
  appear without recompiling.
- **Run all → CSV** runs every report and saves `report_01.csv`, `report_02.csv`, … and `index.txt`
  (row counts) in a folder you choose.

`USE`, `INSERT`, `UPDATE`, `DELETE` and transaction statements in the script are skipped; the
status line at the bottom shows how many reports were loaded and how many statements were skipped.

### Topic 7 questions (Q1–Q5) on the unchanged dataset

| # | Question | Values in the script | Result |
|---|---|---|---|
| Q1 | Passenger list of a flight on a given date | `VN229`, `2026-11-16` | 3 passengers (all tickets cancelled) |
| Q2 | Flights delayed by more than 30 minutes last week | 2026-11-09 to 2026-11-15 | `VN419-1113` (45 min) |
| Q3 | Total baggage weight carried by each aircraft yesterday | `2026-11-18` | AC001, AC002, AC006 |
| Q4 | Crew who worked on every flight of a route in a month | `SGN` → `HKG`, November 2026 | 4 crew members |
| Q5 | Passengers whose baggage was loaded but never delivered | – | 2 bags (LG00079, LG00087) |

The sample data describes the airport on 15 Nov 2026, so the queries use dates from that period
rather than today's date.

---

## 7. Error handling

- Input is checked before it is sent to the database: required fields, formats, numeric ranges
  (bag weight 0–32 kg) and date order (arrival after departure).
- When MySQL refuses an action, the user sees a plain message instead of a stack trace, for example:
  duplicate key (error 1062), deleting a row that other rows still refer to (1451),
  a foreign key value that does not exist (1452), a broken `CHECK` constraint (3819).
- Every connection, statement and result set is closed with try-with-resources.

---

## 8. Integration test (optional)

`src/main/java/com/group7/airport/testing/IntegrationTest.java` runs the whole workflow against
the real database: connection, login, searches, every report of the script, the Topic 7 questions
with other inputs (through `TopicQueryDAO`), insert / update / delete, transactions and constraint
errors. Every test removes the data it wrote.

- NetBeans: right-click the file → **Run File**
- Command line: `mvn compile exec:java -Dexec.mainClass=com.group7.airport.testing.IntegrationTest`

The log is written to `test-results/integration_<time>.txt`, and the result of each report to
`test-results/<time>/report_NN.csv`. The expected numbers assume the dataset script has not been
changed, and that Q1–Q5 stay the first five queries of the query script.

---

## 9. Troubleshooting

| Message | What to do |
|---|---|
| Access denied: wrong MySQL user/password | Correct `db.user` / `db.password` in `db.properties`. |
| Database 'airport_operations' does not exist | Run `sql/00_AirportOperation_dataset.sql`. |
| Table AppUser is missing | Run `sql/01_appuser_setup.sql` (again after every reload of the dataset). |
| Cannot reach MySQL server | Start the MySQL service (Windows: *Services → MySQL80*) and check the host and port in `db.url`. |
| MySQL JDBC driver not found | Maven could not download the driver; connect to the internet and build again. |
| Reports tab is empty | Check that `sql/02_AirportOperation_queries.sql` exists, or start with `-Dreports.file=<path>`, then click **Reload file**. |
| A query from the script is missing in Reports | Only `SELECT`, `WITH` and `CREATE VIEW … AS SELECT` statements become reports. Also check that the statement ends with `;`. |
| Table or view '…' does not exist (Reports) | The query uses a view: run `sql/02_AirportOperation_queries.sql` once in MySQL to create it. |
| Vietnamese names appear as `?` | Keep `characterEncoding=UTF-8` in `db.url`. |
| Old behaviour after editing the code | *Clean and Build* in NetBeans, or `mvn clean package`. |

---

## 10. Group

Group 7 – IT079IU, instructor Assoc. Prof. Dr. Nguyen Thi Thuy Loan.
Java application (task rows 7–8): Bùi Văn Lợi (ITDSIU25021) and Nguyễn Trí Tín (ITDSIU25047).
The full list of members and contributions is in the work-allocation section of the final report.
