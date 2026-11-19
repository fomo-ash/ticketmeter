# Project verification — 19 November 2026

## Result and environment

Reviewed PROJECT_PLAN.md against the Java code, schema, JSPs, and setup instructions. A clean WAR build and the automated integration suite pass on Linux x86-64, running OpenJDK 21.0.13 with compiler target Java 17, Maven 3.9.16, PostgreSQL 17.12, Tomcat 10.1.61, and PostgreSQL JDBC 42.7.14.

Command used (the temporary Maven binary/cache paths are workspace-specific):

```sh
/tmp/apache-maven-3.9.16/bin/mvn -o -Dmaven.repo.local=/tmp/ticketmeter-m2 -Pintegration clean verify
```

Laptop equivalent: `./mvnw -Pintegration clean verify` or `.\mvnw.cmd -Pintegration clean verify`. First execution needs internet access. Result: **8 tests passed: 2 password unit tests and 6 integration scenarios**, with no failures, errors, or skips. Each integration scenario checks multiple assertions/requests. Maven reports are in `target/surefire-reports/` and `target/failsafe-reports/`. The packaged artifact is `target/railway-ticket-demo.war`.

## Verified behavior

| Area | Checks performed |
|---|---|
| Database/bootstrap | Fresh PostgreSQL 17.12; schema and seed loaded twice without duplicate failures; JDBC connectivity; admin bootstrap |
| WAR/JSP | Actual packaged WAR deployed to Tomcat; Root index forward and stylesheet; HTTP search, registration, login, booking/history, complaint, and admin views render; JSP compilation and Jakarta Tags work |
| Accounts | Registration, duplicate-email rejection, incorrect-password rejection, significant password spaces preserved, session login/logout |
| Search | Seeded forward route/date, reverse route returns no bookable result, same stations rejected, invalid/past date rejected |
| Booking | Passenger validation, invalid route, seat/fare/PNR/history, reduced availability, sold-out rejection, rollback on invalid input |
| Concurrency | Two callers compete for one seat: exactly one succeeds; capacity reduction waits for an uncommitted booking and rejects shrinking below its new seat |
| Database safeguards | Unique partial index rejects a duplicate confirmed seat independently of service validation |
| Cancellation | Owner cancellation releases seat; repeated cancellation changes nothing; another passenger cannot cancel it; cancelled seat can be rebooked with a fresh PNR |
| Departed/inactive runs | Booking rejects past, same-day departed, and inactive runs; departed bookings cannot be cancelled |
| Access/escaping | Passenger cannot read another user's history, change admin data, or link complaints to another user's PNR; invalid CSRF token rejected; passenger text escaped in rendered booking HTML; GET logout rejected |
| Complaints/admin | Passenger complaint, admin response/status, passenger sees response; admin adds station/train/route stop and future run; capacity and fare validation; existing ticket fares remain unchanged after fare updates |

## Issues fixed

- Password fields were passed through the generic trimming helper. Leading/trailing password spaces were silently discarded, and whitespace could bypass the intended raw length limit. Passwords now retain their exact value and enforce a 128-character maximum.
- A crafted booking POST with malformed IDs could throw again while rebuilding the error page, yielding HTTP 500. The error path now handles malformed/missing IDs and unavailable runs safely.
- Malformed search requests returned HTTP 200 and could omit the form token. They now return HTTP 400 and keep a working page/token. Date/time errors have a useful form message.
- Logout used a state-changing GET without CSRF validation. It now uses a protected POST form; GET returns 405.
- Admin capacity updates could evaluate occupied seats from an old statement snapshot while waiting for a booking's row lock. They now lock the run, check seats in a fresh statement, and update within one transaction. A regression test holds a booking transaction open to verify this ordering.
- Same-day runs could still be booked/cancelled after departure. Database rules now compare the origin stop's scheduled departure (including day offset) against the database clock.
- Oversized or over-precise fares could produce database errors or silent rounding. The service validates the NUMERIC(10,2) bounds and decimal precision.
- Empty route-stop times are rejected. Partial/invalid admin environment configuration now fails clearly rather than silently skipping account creation.
- Startup checks database/schema availability even when admin bootstrap is omitted. JDBC explicitly loads the packaged PostgreSQL driver for reliable WAR class-loader discovery.
- Email/station/train case normalization uses Locale.ROOT, avoiding machine-locale differences.

## Laptop setup added

- Maven wrapper for Windows and Linux/macOS; pinned clean/resources plugins.
- Multi-stage Dockerfile: Maven/JDK 17 build, Tomcat/JDK 17 runtime.
- Compose: PostgreSQL 17.12 with persistent volume, first-run schema/seed initialization, database readiness gating, app health check, environment-based JDBC configuration, India time zone, and a localhost-only HTTP port.
- Safe `.env.example`, `.dockerignore`, optional shell/PowerShell startup helpers, and detailed Docker/native setup and troubleshooting in README.md.
- Compose configuration validated using Docker Compose v2.32.4: `docker-compose --env-file .env.example config --quiet` succeeded. Shell helper syntax and the missing-Docker error were checked. The committed Maven wrapper successfully downloaded and launched Maven 3.9.16 using an isolated temporary cache.

## Remaining gaps and unverified checks

These do not prevent the seeded demo from running, but the plan should not be marked fully complete:

1. **Docker runtime:** Docker/its engine are absent in this workspace. Compose configuration passes validation, but image builds, container health checks, volume persistence, and startup must still be checked on a Docker-enabled laptop. The same app, PostgreSQL, and Tomcat versions were exercised directly by integration tests.
2. **Browser/viewport:** Browser access was unavailable when attempting visual QA. HTTP/JSP flows pass, but narrow-screen layout, real browser interactions, and keyboard usability remain manual checks. A temporary preview was stopped afterward.
3. **Other platforms/runtime:** Windows/macOS, ARM laptops, native service installation, and a JDK 17 runtime have not been exercised here. Code compiles for Java 17; this workspace runs JDK 21. PostgreSQL's test binaries have platform requirements.
4. **Plan scope:** Admin station/train forms create records; they do not edit existing station/train metadata or deactivate schedules. Admin bookings show the latest 200 records, without the plan's run/date filters. Ticket details appear in booking history rather than a dedicated ticket-detail/print page.
5. **Multi-day routes:** `day_offset` is stored and used for departure eligibility, but search dates still mean the train's starting service date. The UI does not show separate boarding/arrival dates for overnight routes. Use the seeded same-day routes for the current demo.
6. **Schedule integrity/history:** Route-stop administration does not validate the chronological order of the entire route. Editing stops or station/train names can change the route/name shown for existing bookings because those fields are joined live; fare is snapshotted. Finalize routes before taking demo bookings.
7. **Structure/operations:** The implementation is small and uses one Servlet and one repository rather than the plan's separate controller/filter/DAO packages. It uses per-request JDBC connections rather than a pool, and does not include password reset, pagination beyond 200 records, migrations, or production deployment configuration.

The application remains an academic demonstration with one passenger/seat per booking and no payments or real railway integration.
