CREATE TABLE IF NOT EXISTS users (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  email VARCHAR(255) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  role VARCHAR(20) NOT NULL CHECK (role IN ('PASSENGER','ADMIN')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS stations (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  code VARCHAR(10) NOT NULL UNIQUE,
  name VARCHAR(100) NOT NULL,
  city VARCHAR(100) NOT NULL
);
CREATE TABLE IF NOT EXISTS trains (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  number VARCHAR(10) NOT NULL UNIQUE,
  name VARCHAR(100) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT true
);
CREATE TABLE IF NOT EXISTS train_stops (
  train_id BIGINT NOT NULL REFERENCES trains(id),
  station_id BIGINT NOT NULL REFERENCES stations(id),
  stop_order INTEGER NOT NULL CHECK (stop_order > 0),
  arrival TIME,
  departure TIME,
  day_offset INTEGER NOT NULL DEFAULT 0 CHECK (day_offset >= 0),
  PRIMARY KEY (train_id, stop_order),
  UNIQUE (train_id, station_id)
);
CREATE TABLE IF NOT EXISTS train_runs (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  train_id BIGINT NOT NULL REFERENCES trains(id),
  service_date DATE NOT NULL,
  capacity INTEGER NOT NULL CHECK (capacity BETWEEN 1 AND 1000),
  fare NUMERIC(10,2) NOT NULL CHECK (fare >= 0),
  active BOOLEAN NOT NULL DEFAULT true,
  UNIQUE (train_id, service_date)
);
CREATE TABLE IF NOT EXISTS bookings (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  pnr VARCHAR(30) NOT NULL UNIQUE,
  user_id BIGINT NOT NULL REFERENCES users(id),
  run_id BIGINT NOT NULL REFERENCES train_runs(id),
  origin_id BIGINT NOT NULL REFERENCES stations(id),
  destination_id BIGINT NOT NULL REFERENCES stations(id),
  passenger_name VARCHAR(100) NOT NULL,
  passenger_age INTEGER NOT NULL CHECK (passenger_age BETWEEN 1 AND 120),
  seat_number INTEGER NOT NULL CHECK (seat_number > 0),
  fare NUMERIC(10,2) NOT NULL,
  status VARCHAR(20) NOT NULL CHECK (status IN ('CONFIRMED','CANCELLED')),
  booked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  cancelled_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX IF NOT EXISTS one_confirmed_booking_per_seat ON bookings(run_id, seat_number) WHERE status = 'CONFIRMED';
CREATE INDEX IF NOT EXISTS bookings_user_idx ON bookings(user_id, booked_at DESC);
CREATE INDEX IF NOT EXISTS runs_date_idx ON train_runs(service_date, active);
CREATE TABLE IF NOT EXISTS complaints (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id),
  booking_id BIGINT REFERENCES bookings(id),
  category VARCHAR(40) NOT NULL,
  description VARCHAR(2000) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','IN_PROGRESS','RESOLVED')),
  admin_response VARCHAR(2000),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS complaints_user_idx ON complaints(user_id, created_at DESC);
