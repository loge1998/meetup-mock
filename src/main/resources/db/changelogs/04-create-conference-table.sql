CREATE TABLE IF NOT EXISTS conferences (
  name text PRIMARY KEY NOT NULL,
  location text NOT NULL,
  topics text NOT NULL,
  start_datetime TIMESTAMP NOT NULL,
  end_datetime TIMESTAMP NOT NULL,
  available_slots INTEGER NOT NULL
)