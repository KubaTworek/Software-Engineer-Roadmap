CREATE SEQUENCE IF NOT EXISTS click_event_id_seq START WITH 1 INCREMENT BY 100;

ALTER TABLE urls ADD COLUMN IF NOT EXISTS blocked_reason TEXT;
ALTER TABLE urls ADD COLUMN IF NOT EXISTS blocked_at TIMESTAMP WITH TIME ZONE;

CREATE TABLE IF NOT EXISTS click_events (
    id BIGINT DEFAULT nextval('click_event_id_seq') PRIMARY KEY,
    short_code VARCHAR(32) NOT NULL,
    clicked_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ip_hash VARCHAR(128),
    user_agent TEXT,
    referrer TEXT
);

CREATE INDEX IF NOT EXISTS idx_click_events_short_code_clicked_at
    ON click_events(short_code, clicked_at);

CREATE INDEX IF NOT EXISTS idx_click_events_clicked_at
    ON click_events(clicked_at);

CREATE TABLE IF NOT EXISTS url_daily_stats (
    short_code VARCHAR(32) NOT NULL,
    stats_date DATE NOT NULL,
    clicks BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (short_code, stats_date)
);
