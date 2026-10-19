CREATE TABLE event_inventory (
    event_id UUID PRIMARY KEY,
    total_tickets INTEGER NOT NULL CHECK (total_tickets >= 0),
    available_tickets INTEGER NOT NULL CHECK (
        available_tickets >= 0 AND available_tickets <= total_tickets
    )
);

-- Catalog ma opis wydarzenia; ten lokalny model jest autorytatywny dla przydziału.
INSERT INTO event_inventory (event_id, total_tickets, available_tickets) VALUES
('11111111-1111-1111-1111-111111111111', 10000, 10000),
('22222222-2222-2222-2222-222222222222', 5000, 5000);
