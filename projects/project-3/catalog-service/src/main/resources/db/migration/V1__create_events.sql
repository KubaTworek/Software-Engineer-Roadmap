CREATE TABLE events (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    venue VARCHAR(255) NOT NULL,
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    total_tickets INTEGER NOT NULL,
    available_tickets INTEGER NOT NULL
);

INSERT INTO events (id, name, venue, starts_at, total_tickets, available_tickets)
VALUES
('11111111-1111-1111-1111-111111111111', 'BackendConf 2026', 'Warsaw Expo', '2026-10-01T10:00:00Z', 10000, 10000),
('22222222-2222-2222-2222-222222222222', 'Cloud Native Summit', 'Krakow Arena', '2026-11-15T09:00:00Z', 5000, 5000);
