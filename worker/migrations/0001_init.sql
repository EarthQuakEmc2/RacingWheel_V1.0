-- Racing Wheel Pro coach. SQLite / Cloudflare D1.

CREATE TABLE users (
  id TEXT PRIMARY KEY,
  email TEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,
  premium INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL
);

CREATE TABLE auth_sessions (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  token_hash TEXT NOT NULL UNIQUE,
  expires_at TEXT NOT NULL,
  created_at TEXT NOT NULL,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE devices (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  name TEXT NOT NULL,
  token_hash TEXT NOT NULL UNIQUE,
  created_at TEXT NOT NULL,
  last_seen_at TEXT,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE pairing_codes (
  code TEXT PRIMARY KEY,
  poll_secret_hash TEXT NOT NULL UNIQUE,
  device_name TEXT NOT NULL,
  user_id TEXT,
  device_id TEXT,
  device_token TEXT,
  created_at TEXT NOT NULL,
  expires_at TEXT NOT NULL,
  claimed_at TEXT
);

CREATE TABLE sessions (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  device_id TEXT,
  source TEXT NOT NULL,
  track TEXT NOT NULL,
  layout TEXT NOT NULL DEFAULT '',
  car TEXT NOT NULL,
  track_length_m REAL,
  player TEXT NOT NULL DEFAULT '',
  started_at TEXT NOT NULL,
  created_at TEXT NOT NULL,
  lap_count INTEGER NOT NULL DEFAULT 0,
  best_lap_ms INTEGER,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE laps (
  id TEXT PRIMARY KEY,
  session_id TEXT NOT NULL,
  user_id TEXT NOT NULL,
  lap_index INTEGER NOT NULL,
  time_ms INTEGER,
  valid INTEGER NOT NULL DEFAULT 1,
  trace_json TEXT NOT NULL,
  FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE CASCADE
);

CREATE TABLE corner_metrics (
  id TEXT PRIMARY KEY,
  lap_id TEXT NOT NULL,
  session_id TEXT NOT NULL,
  corner_index INTEGER NOT NULL,
  name TEXT NOT NULL,
  apex_norm REAL,
  brake_point_m REAL,
  peak_brake REAL,
  trail_brake_ms REAL,
  min_speed_kmh REAL,
  throttle_pickup_m REAL,
  exit_full_throttle_pct REAL,
  steering_smoothness REAL,
  coasting_ms REAL,
  steer_reversals INTEGER,
  exit_rpm REAL,
  exit_gear INTEGER,
  metrics_json TEXT NOT NULL,
  FOREIGN KEY (lap_id) REFERENCES laps(id) ON DELETE CASCADE
);

CREATE TABLE leaderboard (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  track TEXT NOT NULL,
  layout TEXT NOT NULL,
  car TEXT NOT NULL,
  lap_id TEXT NOT NULL,
  session_id TEXT NOT NULL,
  time_ms INTEGER NOT NULL,
  updated_at TEXT NOT NULL,
  UNIQUE (user_id, track, layout, car)
);

CREATE TABLE ai_reports (
  id TEXT PRIMARY KEY,
  session_id TEXT NOT NULL,
  user_id TEXT NOT NULL,
  metrics_hash TEXT NOT NULL,
  model TEXT NOT NULL,
  report_json TEXT NOT NULL,
  created_at TEXT NOT NULL,
  UNIQUE (session_id, metrics_hash)
);

CREATE TABLE ai_quota (
  user_id TEXT NOT NULL,
  day TEXT NOT NULL,
  used INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (user_id, day)
);

CREATE INDEX idx_sessions_user ON sessions(user_id, started_at DESC);
CREATE INDEX idx_laps_session ON laps(session_id, lap_index);
CREATE INDEX idx_corners_lap ON corner_metrics(lap_id, corner_index);
CREATE INDEX idx_leaderboard_combo ON leaderboard(track, layout, car, time_ms);
CREATE INDEX idx_auth_user ON auth_sessions(user_id);
CREATE INDEX idx_devices_user ON devices(user_id);
