CREATE TABLE IF NOT EXISTS waitlisting_records (
  booking_id UUID PRIMARY KEY NOT NULL,
  conference_name text NOT NULL,
  user_id text NOT NULL,
  created_timestamp TIMESTAMP NOT NULL,
  request_sent BOOLEAN NOT NULL,
  slot_availability_end_time TIMESTAMP
)
