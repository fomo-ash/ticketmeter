# TKTMeter

A Java Servlet/JSP/JDBC/PostgreSQL academic project. Tickets and PNRs are **demo only**, not valid Indian Railways reservations. No payment is collected.

The booking, accounts, schedule administration, and complaints flows are implemented. See [PROJECT_PLAN.md](PROJECT_PLAN.md) for the intended scope and [VERIFICATION.md](VERIFICATION.md) for checks, fixes, and remaining gaps.

## Run on your laptop with Docker

Install Docker with the Compose v2 command (`docker compose`) and start its engine. On Windows/macOS, use Docker Desktop with Linux containers. This path builds Java inside a container: you do not need to install Java, Maven, Tomcat, PostgreSQL, JDBC, or MySQL separately.

From the project folder:

```powershell
# Windows PowerShell
Copy-Item .env.example .env
notepad .env
```

```sh
# Linux/macOS
cp .env.example .env
# Open .env in your editor.
```

Replace both example passwords in `.env` with your own values. Use a 12–128 character admin password. For straightforward Compose parsing, use letters, digits, and hyphens in these local passwords. Leave the database URL alone: Compose configures JDBC to use the `db` service.

Then, on any platform:

```sh
docker compose up --build -d --wait
```

Open **http://localhost:8080/tktmeter/** (or the port set by `RAILWAY_HTTP_PORT` in `.env`). Sign in with the admin email/password from `.env`, or register a passenger. Startup creates the schema, sample stations, eight fictional trains, and runs for tomorrow through the next 14 days. Search **New Delhi → Lucknow**, **New Delhi → Bhubaneswar**, or **Bhubaneswar → Howrah**. See [demo flow.md](demo%20flow.md) for a guided walkthrough.

Useful commands:

```sh
docker compose ps
docker compose logs --tail=100 app db
docker compose stop
# Start again, retaining your users and bookings:
docker compose up -d --wait
# After changing Java/JSP/CSS or pom.xml:
docker compose up --build -d --wait
```

PostgreSQL data lives in the `railway_data` Docker volume. Ordinary stop/down commands retain it. **Do not use `docker compose down -v` unless you intend to erase the demo database.** Database initialization scripts run only when that volume is first created. To refresh expired sample dates without erasing bookings:

```sh
docker compose exec db psql -v ON_ERROR_STOP=1 -U railway_app -d railway_ticket_db -f /docker-entrypoint-initdb.d/02-seed.sql
```

The app is exposed only on localhost; PostgreSQL is accessible inside Compose. To inspect it, run:

```sh
docker compose exec db psql -U railway_app -d railway_ticket_db
```

## Build and test without Docker

Install a JDK (17 is the project target; this workspace also builds with 21). The committed Maven wrapper downloads Maven 3.9.16 on first use. Internet access is needed for the first build.

```powershell
# Windows PowerShell
.\mvnw.cmd clean verify
.\mvnw.cmd -Pintegration clean verify
```

```sh
# Linux/macOS
./mvnw clean verify
./mvnw -Pintegration clean verify
```

The default build runs password unit tests and produces `target/tktmeter.war`. The `integration` profile additionally launches **temporary PostgreSQL 17.12 and Tomcat 10.1.61**, loads schema/seed twice, deploys the WAR, compiles JSPs, and exercises the HTTP and JDBC flows. It does not use or modify your configured application database. It overrides database/admin environment variables in its test subprocess. Port **55439 must be free**; the HTTP test port is chosen automatically. Run as a regular user (PostgreSQL refuses to initialize as root). Native PostgreSQL binaries need a supported OS/architecture and permission to launch processes/open localhost ports. These integration checks have been run on Linux x86-64; Windows/macOS execution still needs validation.

## Native PostgreSQL and Tomcat setup

If you prefer running the services directly, install JDK 17, PostgreSQL 17.12, and Tomcat 10.1.61. Maven supplies PostgreSQL JDBC driver 42.7.14, Jakarta Servlet 6.0, and Jakarta Tags; there is no separate JDBC setup. Do not use Tomcat 9 (`javax.servlet`) or MySQL for this app.

As a PostgreSQL administrator, create a dedicated role/database. Choose your own password:

```sql
CREATE ROLE railway_app LOGIN PASSWORD 'choose-a-strong-local-password';
CREATE DATABASE railway_ticket_db OWNER railway_app;
ALTER DATABASE railway_ticket_db SET timezone TO 'Asia/Kolkata';
```

Run these commands from the project directory. `-h localhost` uses password authentication rather than Unix peer authentication. Enter the database password when prompted:

```sh
psql -h localhost -U railway_app -d railway_ticket_db -v ON_ERROR_STOP=1 -f src/main/resources/db/schema.sql
psql -h localhost -U railway_app -d railway_ticket_db -v ON_ERROR_STOP=1 -f src/main/resources/db/seed.sql
```

Set environment variables in the terminal **that will start Tomcat**:

```powershell
# Windows PowerShell
$env:RAILWAY_DB_URL = 'jdbc:postgresql://localhost:5432/railway_ticket_db'
$env:RAILWAY_DB_USER = 'railway_app'
$env:RAILWAY_DB_PASSWORD = '<your database password>'
$env:RAILWAY_ADMIN_EMAIL = 'admin@example.test'
$env:RAILWAY_ADMIN_PASSWORD = '<your 12–128 character admin password>'
$env:JAVA_OPTS = '-Duser.timezone=Asia/Kolkata'
.\mvnw.cmd clean verify
Copy-Item target/tktmeter.war "$env:CATALINA_HOME/webapps/"
& "$env:CATALINA_HOME/bin/catalina.bat" run
```

```sh
# Linux/macOS; set CATALINA_HOME to your extracted Tomcat directory first.
export RAILWAY_DB_URL='jdbc:postgresql://localhost:5432/railway_ticket_db'
export RAILWAY_DB_USER='railway_app'
export RAILWAY_DB_PASSWORD='<your database password>'
export RAILWAY_ADMIN_EMAIL='admin@example.test'
export RAILWAY_ADMIN_PASSWORD='<your 12–128 character admin password>'
export JAVA_OPTS='-Duser.timezone=Asia/Kolkata'
./mvnw clean verify
cp target/tktmeter.war "$CATALINA_HOME/webapps/"
"$CATALINA_HOME/bin/catalina.sh" run
```

Visit the same URL as the Docker path. A native Tomcat Windows service needs these variables configured for the service; setting them in PowerShell alone does not affect an already running service. Native Tomcat does not read `.env` automatically.

Admin bootstrap creates the account only if its email does not already exist. Changing the environment password later does not reset an existing account or promote a passenger with the same email. Choose a distinct admin email before first startup. Both admin variables may be omitted to run without bootstrapping an admin.

## Demo checklist

1. Register a passenger with a 12+ character password. Log out and back in.
2. Search New Delhi → Lucknow for tomorrow; note available seats and fare.
3. Book one passenger, verify the demo PNR, passenger, fare, and seat in My bookings.
4. Cancel; check that the seat becomes available again.
5. Submit a complaint linked to your PNR. Log in as admin, respond, then check the passenger's complaint status.
6. As admin, add a station, train, route stops, and future run. Search the new route as a passenger.

## Troubleshooting

- **Port 8080 in use:** set `RAILWAY_HTTP_PORT=8081` in `.env`, rerun Compose, and open port 8081. Native Tomcat's port is configured in `conf/server.xml`.
- **Docker command/engine missing:** install/start Docker or use the native setup above. Check `docker version` and `docker compose version`.
- **Database authentication fails after changing `.env`:** an existing volume retains its original database password. Restore the original value or deliberately change the role password inside PostgreSQL; editing `.env` alone does not update the database role.
- **App fails to start:** inspect `docker compose logs app db` or native Tomcat logs. Confirm password, database name, schema, and both admin variables. Database credentials must be present in the Tomcat process environment.
- **No search results:** choose a seeded route in the forward direction and a date in the next 14 days; refresh the seed as above if needed.
- **First build/download fails:** check internet/proxy settings. Maven downloads dependencies; Docker also downloads three base images.
