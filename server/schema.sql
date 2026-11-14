CREATE TABLE IF NOT EXISTS devices (
  id TEXT PRIMARY KEY,
  device_name TEXT NOT NULL,
  token_hash TEXT NOT NULL UNIQUE,
  created_at_ms INTEGER NOT NULL,
  last_seen_ms INTEGER,
  model TEXT,
  android_version TEXT,
  root_available INTEGER,
  screen_width INTEGER,
  screen_height INTEGER
);

CREATE TABLE IF NOT EXISTS commands (
  id TEXT PRIMARY KEY,
  device_id TEXT NOT NULL,
  tool_name TEXT NOT NULL,
  payload_json TEXT NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('queued', 'delivered', 'completed', 'rejected', 'failed', 'expired')),
  result_json TEXT,
  created_at_ms INTEGER NOT NULL,
  updated_at_ms INTEGER NOT NULL,
  expires_at_ms INTEGER NOT NULL,
  FOREIGN KEY (device_id) REFERENCES devices(id)
);

CREATE INDEX IF NOT EXISTS idx_commands_device_status_created
  ON commands(device_id, status, created_at_ms);

CREATE INDEX IF NOT EXISTS idx_commands_expiry
  ON commands(expires_at_ms);
