ALTER TABLE click_events ADD COLUMN IF NOT EXISTS event_id VARCHAR(64);
ALTER TABLE click_events ADD COLUMN IF NOT EXISTS referrer_domain VARCHAR(255);
ALTER TABLE click_events ADD COLUMN IF NOT EXISTS country VARCHAR(8);
ALTER TABLE click_events ADD COLUMN IF NOT EXISTS device_type VARCHAR(32);
ALTER TABLE click_events ADD COLUMN IF NOT EXISTS browser VARCHAR(32);
ALTER TABLE click_events ADD COLUMN IF NOT EXISTS suspicious BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE click_events ADD COLUMN IF NOT EXISTS abuse_reason TEXT;

UPDATE click_events
SET event_id = 'legacy-' || CAST(id AS VARCHAR)
WHERE event_id IS NULL;

ALTER TABLE click_events ALTER COLUMN event_id SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_click_events_event_id ON click_events(event_id);
CREATE INDEX IF NOT EXISTS idx_click_events_country ON click_events(country);
CREATE INDEX IF NOT EXISTS idx_click_events_device_type ON click_events(device_type);
CREATE INDEX IF NOT EXISTS idx_click_events_referrer_domain ON click_events(referrer_domain);
CREATE INDEX IF NOT EXISTS idx_click_events_suspicious ON click_events(suspicious);
