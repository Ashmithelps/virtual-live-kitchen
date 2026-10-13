-- When the station's paired phone last called the API (heartbeat), for the owner's online/offline indicator.
ALTER TABLE kitchen_stations ADD COLUMN device_last_seen_at TIMESTAMPTZ;
