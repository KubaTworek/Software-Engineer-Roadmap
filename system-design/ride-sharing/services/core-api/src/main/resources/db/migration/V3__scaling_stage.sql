ALTER TABLE rides ADD COLUMN IF NOT EXISTS shard_key VARCHAR(64);
ALTER TABLE rides ADD COLUMN IF NOT EXISTS risk_score INT DEFAULT 0;
ALTER TABLE rides ADD COLUMN IF NOT EXISTS pricing_snapshot JSONB;

CREATE INDEX IF NOT EXISTS idx_rides_city_status_requested ON rides(city_id, status, requested_at);
CREATE INDEX IF NOT EXISTS idx_rides_shard_key ON rides(shard_key);

CREATE TABLE IF NOT EXISTS city_shards (
  city_id VARCHAR(64) PRIMARY KEY,
  shard_name VARCHAR(128) NOT NULL,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT now()
);

INSERT INTO city_shards(city_id, shard_name) VALUES
  ('warsaw', 'shard-pl-1'),
  ('krakow', 'shard-pl-1'),
  ('gdansk', 'shard-pl-1'),
  ('berlin', 'shard-de-1')
ON CONFLICT (city_id) DO UPDATE SET shard_name = EXCLUDED.shard_name;
