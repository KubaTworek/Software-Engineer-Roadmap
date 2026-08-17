CREATE SEQUENCE IF NOT EXISTS url_id_seq START WITH 1000000 INCREMENT BY 50;

CREATE TABLE urls (
    id BIGINT DEFAULT nextval('url_id_seq') PRIMARY KEY,
    short_code VARCHAR(32) UNIQUE,
    long_url TEXT NOT NULL,
    user_id BIGINT,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE
);

CREATE UNIQUE INDEX idx_urls_short_code ON urls(short_code);
CREATE INDEX idx_urls_user_id ON urls(user_id);
CREATE INDEX idx_urls_expires_at ON urls(expires_at);
CREATE INDEX idx_urls_status ON urls(status);
