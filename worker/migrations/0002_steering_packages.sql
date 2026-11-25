-- Steering Package orders and entitlements.
-- Amounts and package ids only. Payment credentials are not stored.

CREATE TABLE orders (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  provider TEXT NOT NULL,
  provider_ref TEXT,
  status TEXT NOT NULL,
  currency TEXT NOT NULL,
  amount_cents INTEGER NOT NULL,
  items_json TEXT NOT NULL,
  created_at TEXT NOT NULL,
  paid_at TEXT,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE entitlements (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  package_id TEXT NOT NULL,
  source_order_id TEXT NOT NULL,
  created_at TEXT NOT NULL,
  UNIQUE (user_id, package_id),
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_orders_user ON orders(user_id, created_at DESC);
CREATE INDEX idx_entitlements_user ON entitlements(user_id);
