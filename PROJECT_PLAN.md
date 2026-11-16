# Indian Railways Ticket Management System — Team Project Plan

## 1. Goal and boundaries

Build a Java web application that demonstrates railway ticket booking against **sample trains and seat inventory stored in PostgreSQL**. A passenger can register, search trains, book a seat, receive a project PNR, view the ticket, and cancel it. An admin can maintain schedules and inspect bookings. Add passenger complaints after the booking flow is complete.

This is an academic demo. It does not connect to Indian Railways or IRCTC, does not issue valid railway tickets, and does not accept payments. Label tickets and PNRs as **demo only** in the UI. The implementation is organized into the six phases below; complaint tracking is included in phase 6.

### Definition of done for the booking milestone

On a fresh database, a teammate can load sample schedules, start the app, register a passenger, find a train for a date, book an available seat, see the PNR in booking history, and cancel the booking. Availability changes correctly after booking and cancellation. An admin can add or edit a future schedule.

## 2. Prerequisites and exact team versions

Use these versions for the first build so everyone has the same environment. The versions were checked against official project pages on **16 November 2026**.

| Component | Version | Why it is needed | Installation/check |
|---|---:|---|---|
| Java Development Kit | **17** | Compile and run Java | Install a JDK 17 distribution if needed. Run `java -version` and `javac -version`. This workspace already has Microsoft OpenJDK **17.0.16**. |
| Apache Maven | **3.9.16** | Download dependencies and build the WAR | Download the binary ZIP from [Apache Maven](https://maven.apache.org/download.cgi), extract it, add its `bin` folder to Windows `PATH`, then run `mvn -version`. |
| Apache Tomcat | **10.1.61** | Run Servlets and JSP | Download the Windows ZIP or installer from [Apache Tomcat 10](https://tomcat.apache.org/download-10), start it, and open `http://localhost:8080`. Tomcat 10.1 uses **Jakarta Servlet 6.0** and **JSP 3.1**. |
| PostgreSQL | **17.12** | Store users, trains, and bookings | Get the Windows installer through [PostgreSQL](https://www.postgresql.org/download/windows/). Include pgAdmin. Record the database administrator password and check the server is running. |
| PostgreSQL JDBC driver | **42.7.14** | Connect Java to PostgreSQL | Add `org.postgresql:postgresql:42.7.14` to Maven; Maven downloads it. [pgJDBC downloads](https://jdbc.postgresql.org/download/). |
| IDE/editor | Team choice | Edit and debug code | IntelliJ IDEA Community, Eclipse, or VS Code with Java extensions. Do not depend on IDE-specific project settings. |
| Git | Current stable version | Share code and review changes | Install if absent; check `git --version`. |

**JDBC is a Java API, not another server to install.** PostgreSQL is the database, so MySQL and MySQL Connector/J are not needed. Use `jakarta.servlet.*` imports with Tomcat 10.1; older `javax.servlet.*` tutorials target a different stack. The Servlet API must be a Maven dependency with `provided` scope because Tomcat supplies it at runtime.

### Windows setup sequence

1. Confirm `java -version` and `javac -version` both show 17. Set `JAVA_HOME` to the JDK folder if Maven or Tomcat cannot find Java.
2. Install Maven, open a **new** PowerShell window, and confirm `mvn -version` shows Maven 3.9.16 and Java 17.
3. Install PostgreSQL with pgAdmin. Create a database named `railway_ticket_db` and a dedicated login role for the app. Give that role access to this database; keep its password out of Git.
4. Install and start Tomcat. Confirm its welcome page at `http://localhost:8080`. If port 8080 is occupied, change Tomcat's HTTP port and document the replacement.
5. Clone the project, run the schema and seed SQL scripts, set local database credentials, run `mvn clean package`, and deploy the generated `.war` from `target/` to Tomcat's `webapps/` folder.
6. Open the deployed application at `http://localhost:8080/<war-name>/` and run the demo checklist in section 8.

## 3. Architecture and repository layout

Use a **Maven WAR** project with server rendered pages. Do not introduce Spring Boot or a JavaScript framework; the purpose is to demonstrate JDBC, Servlets, JSP, HTML, CSS, and core Java.

```text
pom.xml
src/main/java/.../model/       Java data objects
src/main/java/.../dao/         JDBC queries and row mapping
src/main/java/.../service/     Booking rules and transactions
src/main/java/.../servlet/     HTTP routes and form handling
src/main/java/.../filter/      Login and admin access checks
src/main/webapp/WEB-INF/jsp/   JSP views (not directly public)
src/main/webapp/assets/        CSS and optional images
src/main/resources/            Non-secret configuration
src/main/resources/db/         Versioned schema and sample data SQL
src/test/java/                 Focused tests
README.md                      Quick start and demo account instructions
```

Request flow: **browser form → Servlet → service → DAO/JDBC → PostgreSQL → Servlet → JSP → HTML response**. Servlets parse requests and set view data; services own rules and transactions; DAO classes own SQL; JSP files render data with Expression Language and tags. Avoid Java scriptlets and SQL in JSP.

Build dependencies should include the PostgreSQL driver, `jakarta.servlet-api` 6.0.0 (`provided`), a JSP/Jakarta Tags implementation compatible with Tomcat 10.1, and JUnit for focused tests. Pin all dependency and Maven plugin versions in `pom.xml` before the first team build. Set the Maven compiler release to 17 and packaging to `war`.

## 4. Data model

Create schema SQL and repeatable demo seed SQL. Use generated primary keys, foreign keys, unique constraints, and indexes for search fields.

| Table | Main fields and purpose |
|---|---|
| `users` | ID, name, unique email, password hash, role (`PASSENGER` or `ADMIN`), created time. |
| `stations` | ID, unique station code, name, city/state. |
| `trains` | ID, unique train number, name, active flag. |
| `train_stops` | Train ID, station ID, stop order, arrival/departure time, day offset. Supports route display and origin/destination search in the correct order. |
| `train_runs` | ID, train ID, service date, seat capacity, fare, status. A run is one train on one date. Unique `(train_id, service_date)`. |
| `bookings` | ID, unique demo PNR, user ID, run ID, passenger name/age, origin and destination station IDs, seat number, fare snapshot, status (`CONFIRMED` or `CANCELLED`), booked/cancelled times. |

For the first milestone, **one booking is one passenger and one seat**. A user may make multiple bookings. Only a confirmed booking occupies a seat. Enforce at the database level that a run cannot have two confirmed bookings for the same seat, including when requests happen at the same time. Use a transaction for booking and cancellation. Do not reuse a PNR after cancellation.

Seed a small, clearly fictional/demo schedule across several Indian stations and dates. Keep the seed deterministic so every teammate can reproduce the same demonstration.

## 5. Pages and behavior

### Passenger

1. **Home/search:** select origin, destination, and travel date. Show runs whose route contains origin before destination, with departure, arrival, fare, and available seats.
2. **Register/login/logout:** email and password authentication using a server session. Hash passwords; never store or display plaintext passwords.
3. **Book:** require login; collect passenger name and age; show the selected run and fare; validate again on submit; allocate an available seat and create the booking atomically; show confirmation and demo PNR.
4. **My bookings:** list the signed-in user's bookings and statuses. Ticket detail shows passenger, train, date, route, seat, fare, and PNR.
5. **Cancel:** allow the booking owner to cancel a future confirmed booking. Update status and release the seat atomically; repeated cancellation must not create another change.

### Admin

1. Sign in with an admin account created by the seed/setup process.
2. Create or edit stations, trains, route stops, and future train runs/capacity.
3. List bookings by run and date. Admin URLs must reject passenger sessions.

### Common rules

- Validate required fields, station order, date, passenger age, capacity, and input lengths on the server. Display useful form errors.
- Use `PreparedStatement` parameters for all SQL. Escape user-supplied content in JSP output.
- Use POST for booking, cancellation, login, and admin updates; redirect after successful POST to prevent accidental resubmission.
- Store database connection values in local environment variables or an untracked local configuration file. Commit a safe example configuration only.
- Keep a simple, responsive HTML/CSS layout that works on a laptop and phone.

## 6. Milestones and team work order

| Milestone | Deliverable | Completion check |
|---|---|---|
| 1. Environment and skeleton | Maven WAR, Tomcat deployment, one Servlet forwarding to a JSP, shared README | Clean `mvn clean package`; page loads in Tomcat. |
| 2. Database | Schema, seed data, JDBC connection, DAOs for stations and trains | Sample trains can be read from PostgreSQL. |
| 3. Accounts and search | Registration, login/session, role filter, dated route search | Passenger finds a seeded run; admin route is protected. |
| 4. Booking | Seat allocation transaction, PNR, ticket view, history, cancellation | Two users cannot obtain the same last seat; cancelled seat becomes available. |
| 5. Admin and UI | Schedule forms, booking list, validation, responsive styling | Admin creates a future run and passenger can book it. |
| 6. Complaints and verification | Complaint submission/status, admin response, tests, clean setup instructions, demo script | Passenger can track a complaint linked to a booking; a new teammate can run the complete demo from the repository. |

Suggested division: one teammate owns schema/DAO, one owns passenger Servlets and services, one owns JSP/CSS, and one owns admin flows and integration testing. Agree on model fields and route names before parallel implementation; merge a working vertical slice at each milestone.

## 7. Booking transaction design

On booking submit, the service starts a database transaction, locks the selected `train_runs` row, verifies the run is future/active and the requested route is valid, finds a free seat from `1..capacity`, inserts the confirmed booking with a unique PNR, then commits. If there is no seat, roll back and return a friendly “sold out” result. The database uniqueness constraint remains the final safeguard against duplicate seat assignment. On cancellation, verify ownership and status, mark the booking cancelled in a transaction, then commit.

Generate the PNR as a project identifier, not a real railway PNR. Never infer available seats from a value cached in the browser; recalculate from confirmed bookings when processing the request.

## 8. Test and demo checklist

- Fresh setup: schema and seed scripts run, WAR builds, Tomcat starts, and search page loads.
- Register, log in, log out, and reject invalid credentials or duplicate email.
- Search by station/date; reject same origin and destination, reversed route, and past dates.
- Book an available seat; verify unique PNR, correct passenger details, fare, and reduced availability.
- Send two concurrent booking requests for the final seat; exactly one succeeds.
- Reject booking when full and reject access to another passenger's ticket.
- Cancel a future booking; history shows `CANCELLED`, seat becomes available, and a second cancellation changes nothing.
- Reject passenger access to admin URLs; admin can add a future run and see its bookings.
- Verify forms on a narrow browser viewport and confirm no plaintext credentials are committed.

## 9. Complaints in phase 6

After booking acceptance, add a `complaints` table with ID, user ID, optional booking ID, category, description, status (`OPEN`, `IN_PROGRESS`, `RESOLVED`), admin response, and timestamps. Passengers submit and track their own complaints; admins list, update status, and respond.

## 10. Sources for the pinned versions

- [Tomcat supported versions and Servlet/JSP compatibility](https://tomcat.apache.org/whichversion.html)
- [Maven downloads](https://maven.apache.org/download.cgi)
- [PostgreSQL release notes](https://www.postgresql.org/docs/release/)
- [PostgreSQL Windows installation](https://www.postgresql.org/download/windows/)
- [PostgreSQL JDBC driver downloads](https://jdbc.postgresql.org/download/)
