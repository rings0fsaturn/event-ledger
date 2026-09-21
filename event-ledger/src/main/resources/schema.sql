CREATE TABLE IF NOT EXISTS events (
    account_id text NOT NULL,
    idempotency_key text NOT NULL,
    event_type text NOT NULL,
    occurred_at timestamptz NOT NULL,
    received_at timestamptz NOT NULL DEFAULT now(),
    payload jsonb NOT NULL,
    PRIMARY KEY (account_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS reservations (
  reservation_id text        PRIMARY KEY,
  order_id       text        NOT NULL,
  account_id     text        NOT NULL,
  sku            text        NOT NULL,
  quantity       int         NOT NULL CHECK (quantity > 0),
  expires_at     timestamptz NOT NULL,
  resolved_at    timestamptz
);

CREATE UNIQUE INDEX IF NOT EXISTS reservations_one_per_order
  ON reservations (order_id) WHERE resolved_at IS NULL;

CREATE TABLE IF NOT EXISTS reservation_resolution (
  reservation_id text PRIMARY KEY,
  outcome        text NOT NULL CHECK (outcome IN ('captured','refunded','expired')),
  event_key      text NOT NULL
);

CREATE TABLE IF NOT EXISTS refund_intent (
  reservation_id text PRIMARY KEY,
  order_id       text NOT NULL,
  reason         text NOT NULL,
  created_at     timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS stock_levels (
  sku                     text PRIMARY KEY,
  total                   int NOT NULL,
  reserved                int NOT NULL DEFAULT 0,
  sold                    int NOT NULL DEFAULT 0,
  reservation_ttl_seconds int NOT NULL
);


ALTER TABLE stock_levels DROP CONSTRAINT IF EXISTS stock_levels_reserved_non_negative;
ALTER TABLE stock_levels ADD CONSTRAINT stock_levels_reserved_non_negative CHECK (reserved >= 0);

ALTER TABLE stock_levels DROP CONSTRAINT IF EXISTS stock_levels_sold_non_negative;
ALTER TABLE stock_levels ADD CONSTRAINT stock_levels_sold_non_negative CHECK (sold >= 0);

ALTER TABLE stock_levels DROP CONSTRAINT IF EXISTS stock_levels_total_non_negative;
ALTER TABLE stock_levels ADD CONSTRAINT stock_levels_total_non_negative CHECK (total >= 0);

INSERT INTO stock_levels (sku, total, reserved, sold, reservation_ttl_seconds)
VALUES ('PHONE-X-256-BLK', 100, 0, 0, 900)
ON CONFLICT (sku) DO NOTHING;
