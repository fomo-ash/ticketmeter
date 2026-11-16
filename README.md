# Railway Ticket Demo

A Servlet/JSP/JDBC/PostgreSQL academic project. Tickets and PNRs are **demo only** and are not valid Indian Railways reservations. No payment is collected.

See [PROJECT_PLAN.md](PROJECT_PLAN.md) for the team plan, versions, database model, and acceptance checklist.

## Required tools

- JDK 17
- Apache Maven 3.9.16
- Apache Tomcat 10.1.61
- PostgreSQL 17.12 (pgAdmin optional but useful)

The PostgreSQL JDBC driver 42.7.14 and Jakarta libraries are downloaded by Maven. MySQL is not needed.

## Database setup

Create a local database and role using pgAdmin or `psql` as a PostgreSQL administrator. Choose your own password; do not commit it.

```sql
CREATE ROLE railway_app LOGIN PASSWORD 'choose-a-strong-local-password';
CREATE DATABASE railway_ticket_db OWNER railway_app;
```

Run the SQL files in order **as `railway_app` connected to `railway_ticket_db`**:

```powershell
psql -U railway_app -d railway_ticket_db -f src/main/resources/db/schema.sql
psql -U railway_app -d railway_ticket_db -f src/main/resources/db/seed.sql
```

The seed provides five stations, two fictional demo trains, and runs for the next 14 days. Re-running it does not duplicate rows.

## Local configuration

Set these environment variables in the terminal or Tomcat service configuration **before starting Tomcat**:

```powershell
$env:RAILWAY_DB_URL = 'jdbc:postgresql://localhost:5432/railway_ticket_db'
$env:RAILWAY_DB_USER = 'railway_app'
$env:RAILWAY_DB_PASSWORD = '<your local password>'
$env:RAILWAY_ADMIN_EMAIL = 'admin@example.test'
$env:RAILWAY_ADMIN_PASSWORD = '<choose at least 12 characters>'
```

The application creates the admin account on first startup if both admin variables are present. It does not reset an existing admin password on subsequent starts. If Tomcat runs as a Windows service, configure variables for that service rather than only in PowerShell.

## Build and run

```powershell
mvn clean package
```

Copy `target/railway-ticket-demo.war` to Tomcat's `webapps` folder and start Tomcat. Visit:

```text
http://localhost:8080/railway-ticket-demo/
```

The root page forwards to `/app/`. If Tomcat uses another port, replace `8080`.

## Demo flow

1. Register a passenger with a password of at least 12 characters.
2. Search New Delhi → Lucknow or New Delhi → Mumbai for a date within the next 14 days.
3. Book one passenger, note the demo PNR and assigned seat, then view **My bookings**.
4. Cancel the booking and search again to see the seat return to availability.
5. Submit a complaint and see its status. Log in as admin to respond, then check the passenger view again.
6. As admin, add a future run, station, train, and route stops.

## Current implementation notes

- One booking contains one passenger and one seat. A user can make multiple bookings.
- Seat assignment uses a PostgreSQL row lock and a unique partial index to prevent double booking.
- Passwords use PBKDF2-HMAC-SHA256 with per-user salt.
- The app uses `jakarta.servlet.*`, which matches Tomcat 10.1.
- Passengers can submit complaints, optionally linked to their own booking; admins can respond and update status.
