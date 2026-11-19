# Demo flow

This is an academic railway booking demo. Tickets and PNRs are fictional, and no payment is collected.

## Start the app

1. From the project folder, run `docker compose up --build -d --wait`.
2. Open `http://localhost:8080/railway-ticket-demo/app/` by default. If you changed `RAILWAY_HTTP_PORT` in `.env`, use that port instead (this workspace currently uses 8081).
3. For an existing Docker database volume, load the expanded sample stations and trains once with:

   ```sh
   docker compose exec db psql -v ON_ERROR_STOP=1 -U railway_app -d railway_ticket_db -f /docker-entrypoint-initdb.d/02-seed.sql
   ```

   The seed is safe to rerun. It adds missing sample records and schedules runs for the next 14 days without replacing existing bookings.

## Passenger journey

1. On **Find trains**, choose **New Delhi (NDLS)** to **Lucknow (LKO)** for tomorrow. Two departures should appear. You can also try **New Delhi (NDLS)** to **Bhubaneswar (BBS)** for two more options, or **Bhubaneswar (BBS)** to **Howrah Junction (HWH)**.
2. Select **Book seat** on a train. If you are signed out, choose **Create account** and register a passenger with a password of at least 12 characters. Return to the search and choose a train again.
3. Enter the passenger name and age, then confirm the demo booking. The **My bookings** page shows the demo PNR, route, fare, and seat.
4. Submit a complaint under **Complaints** and enter the demo PNR to link it to the booking.
5. Cancel the booking from **My bookings**. Search the same train again to see the seat become available.

## Admin login and actions

1. Open **Log in**. Admin and passenger accounts use the same page.
2. Enter the email from `RAILWAY_ADMIN_EMAIL` and password from `RAILWAY_ADMIN_PASSWORD` in your local `.env` file. The admin account is created on first startup. If you later change the password in `.env`, it does not reset an existing account.
3. After login, open **Admin**. You can add a station, train, route stops, and a future run. A newly added train needs route stops and a run before it appears in search.
4. In **Complaints**, set the passenger complaint to **Resolved**, enter a response, and save it. The passenger can then view that response on their Complaints page.

## Sample network

The seed includes nine stations: New Delhi, Kanpur Central, Lucknow, Mumbai Central, Jaipur Junction, Bhubaneswar, Prayagraj Junction, Cuttack, and Howrah Junction. There are eight fictional trains and daily runs for the next 14 days. Routes are directional; select the train's actual origin and destination order.
