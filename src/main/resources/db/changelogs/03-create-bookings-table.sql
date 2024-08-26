CREATE TABLE IF NOT EXISTS bookings (
  id UUID PRIMARY KEY NOT NULL,
  conference_name text NOT NULL,
  user_id text NOT NULL,
  created_timestamp TIMESTAMP NOT NULL,
  status VARCHAR(50) NOT NULL
)