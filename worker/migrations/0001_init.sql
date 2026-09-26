-- Potr Bonsai Master: initial schema (Cloudflare D1 / SQLite)

-- Staff who can use the Android app. Auth is via long random access keys
-- (only their SHA-256 is stored), so no slow password hashing is needed on
-- the Workers free tier CPU budget.
CREATE TABLE users (
  id          TEXT PRIMARY KEY,
  name        TEXT NOT NULL,
  email       TEXT,
  role        TEXT NOT NULL CHECK (role IN ('admin', 'staff')),
  key_hash    TEXT NOT NULL UNIQUE,
  active      INTEGER NOT NULL DEFAULT 1,
  created_at  TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
  last_seen_at TEXT
);

-- Sequential human-readable codes (POTR-B-00001, POTR-P-00001).
CREATE TABLE counters (
  name  TEXT PRIMARY KEY,
  value INTEGER NOT NULL
);
INSERT INTO counters (name, value) VALUES ('bonsai', 0), ('pot', 0);

-- Each pot is a unique piece and a product category of its own.
CREATE TABLE pots (
  id             TEXT PRIMARY KEY,
  code           TEXT NOT NULL UNIQUE,
  name           TEXT NOT NULL,
  maker          TEXT,               -- potter / studio
  origin         TEXT,               -- country / region of make
  material       TEXT,               -- stoneware, porcelain, mica ...
  glaze          TEXT,               -- unglazed, glazed + colour
  shape          TEXT,               -- rectangle, oval, round, cascade ...
  length_cm      REAL,
  width_cm       REAL,
  height_cm      REAL,
  year_made      INTEGER,
  condition      TEXT,
  description    TEXT,
  internal_notes TEXT,
  cost_inr       REAL,
  status         TEXT NOT NULL DEFAULT 'in_stock'
                 CHECK (status IN ('in_stock', 'in_use', 'reserved', 'sold', 'retired')),
  for_sale       INTEGER NOT NULL DEFAULT 0,
  price_inr      REAL,
  cover_photo_id TEXT,
  woo_product_id INTEGER,
  woo_sync_status TEXT NOT NULL DEFAULT 'none'
                 CHECK (woo_sync_status IN ('none', 'pending', 'synced', 'error')),
  woo_sync_error TEXT,
  woo_image_ids  TEXT,               -- JSON photo ids last sent to the shop
  created_at     TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
  updated_at     TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

CREATE TABLE bonsai (
  id              TEXT PRIMARY KEY,
  code            TEXT NOT NULL UNIQUE,
  name            TEXT,
  species_common  TEXT NOT NULL,
  species_botanical TEXT,
  style           TEXT,              -- formal upright, cascade, literati ...
  -- Origin & provenance
  origin_type     TEXT CHECK (origin_type IN ('seed', 'cutting', 'air_layer', 'graft',
                    'nursery_stock', 'yamadori', 'import', 'other')),
  origin_location TEXT,
  source_name     TEXT,              -- nursery / collector / artist it came from
  acquired_on     TEXT,              -- ISO date
  -- Age: stored as a start year so it stays correct over time
  start_year      INTEGER,
  age_basis       TEXT CHECK (age_basis IN ('documented', 'estimated')),
  -- Measurements at last update
  height_cm       REAL,
  trunk_cm        REAL,              -- trunk diameter at base
  spread_cm       REAL,
  pot_id          TEXT REFERENCES pots(id),
  location        TEXT,              -- where it sits in the nursery (bench / zone)
  description     TEXT,              -- public
  internal_notes  TEXT,              -- private
  cost_inr        REAL,              -- private
  status          TEXT NOT NULL DEFAULT 'active'
                  CHECK (status IN ('active', 'reserved', 'sold', 'deceased', 'archived')),
  for_sale        INTEGER NOT NULL DEFAULT 0,
  price_inr       REAL,
  cover_photo_id  TEXT,
  woo_product_id  INTEGER,
  woo_sync_status TEXT NOT NULL DEFAULT 'none'
                  CHECK (woo_sync_status IN ('none', 'pending', 'synced', 'error')),
  woo_sync_error  TEXT,
  woo_image_ids   TEXT,              -- JSON photo ids last sent to the shop
  created_at      TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
  updated_at      TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);
CREATE INDEX bonsai_status ON bonsai (status, for_sale);
CREATE INDEX bonsai_pot ON bonsai (pot_id);

-- Append-only care history. Each row is chained to the previous row of the
-- same bonsai with SHA-256 so the certificate can prove history was not
-- rewritten. Corrections are made by adding a 'correction' event.
CREATE TABLE events (
  id            TEXT PRIMARY KEY,
  bonsai_id     TEXT NOT NULL REFERENCES bonsai(id),
  seq           INTEGER NOT NULL,
  type          TEXT NOT NULL,
  performed_on  TEXT NOT NULL,       -- ISO date the work was done
  performed_by  TEXT,                -- name shown on certificate
  notes         TEXT,
  details       TEXT NOT NULL DEFAULT '{}', -- JSON, type specific
  photo_hashes  TEXT NOT NULL DEFAULT '[]', -- JSON array of sha256 of attached photos
  is_public     INTEGER NOT NULL DEFAULT 1,
  recorded_by   TEXT REFERENCES users(id),
  created_at    TEXT NOT NULL,
  prev_hash     TEXT NOT NULL,
  hash          TEXT NOT NULL UNIQUE,
  UNIQUE (bonsai_id, seq)
);
CREATE INDEX events_bonsai ON events (bonsai_id, seq);

CREATE TABLE photos (
  id            TEXT PRIMARY KEY,
  subject_type  TEXT NOT NULL CHECK (subject_type IN ('bonsai', 'pot')),
  subject_id    TEXT NOT NULL,
  event_id      TEXT REFERENCES events(id),
  r2_key        TEXT NOT NULL UNIQUE,
  content_type  TEXT NOT NULL,
  bytes         INTEGER NOT NULL,
  sha256        TEXT NOT NULL,
  caption       TEXT,
  taken_on      TEXT,
  uploaded_by   TEXT REFERENCES users(id),
  created_at    TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);
CREATE INDEX photos_subject ON photos (subject_type, subject_id, created_at);

-- Scheduled care (pruning, repotting, wiring checks ...).
CREATE TABLE tasks (
  id              TEXT PRIMARY KEY,
  bonsai_id       TEXT NOT NULL REFERENCES bonsai(id),
  type            TEXT NOT NULL,
  title           TEXT,
  due_on          TEXT NOT NULL,     -- ISO date
  notes           TEXT,
  recurrence_days INTEGER,           -- if set, completing creates the next task
  status          TEXT NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'done', 'cancelled')),
  assigned_to     TEXT REFERENCES users(id),
  completed_event_id TEXT REFERENCES events(id),
  created_by      TEXT REFERENCES users(id),
  created_at      TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
  completed_at    TEXT
);
CREATE INDEX tasks_due ON tasks (status, due_on);
CREATE INDEX tasks_bonsai ON tasks (bonsai_id, status);

-- Processed WooCommerce orders (idempotent webhook handling).
CREATE TABLE woo_orders (
  order_id    INTEGER PRIMARY KEY,
  status      TEXT NOT NULL,
  codes       TEXT NOT NULL,         -- JSON array of SKUs we matched
  received_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);
