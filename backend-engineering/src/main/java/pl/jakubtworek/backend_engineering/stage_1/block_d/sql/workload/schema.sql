-- Użytkownicy i konta (balans, wersja do blokady optymistycznej)
CREATE TABLE users (
                       id SERIAL PRIMARY KEY, name TEXT NOT NULL, created_at TIMESTAMP NOT NULL
);
CREATE TABLE accounts (
                          id SERIAL PRIMARY KEY, user_id INT REFERENCES users(id),
                          balance NUMERIC NOT NULL, version INT NOT NULL DEFAULT 0
);
-- Zamówienia i pozycje
CREATE TABLE orders (
                        id SERIAL PRIMARY KEY, user_id INT REFERENCES users(id),
                        status TEXT, created_at TIMESTAMP
);
CREATE TABLE order_items (
                             id SERIAL PRIMARY KEY, order_id INT REFERENCES orders(id),
                             product_id INT, quantity INT
);
-- Płatności
CREATE TABLE payments (
                          id SERIAL PRIMARY KEY, order_id INT REFERENCES orders(id),
                          status TEXT, amount NUMERIC
);
-- Tabela outbox (do wzorca Outbox Pattern)
CREATE TABLE outbox (
                        id SERIAL PRIMARY KEY,
                        aggregate TEXT NOT NULL,
                        aggregate_id INT NOT NULL,
                        type TEXT NOT NULL,
                        payload JSONB NOT NULL,
                        processed BOOLEAN NOT NULL DEFAULT FALSE
);

-- Przykładowe dane
INSERT INTO users (name, created_at) VALUES
                                         ('Ala', '2025-01-01'), ('Olek', '2025-01-02');
INSERT INTO accounts (user_id, balance) VALUES
                                            (1, 1000), (2, 500);
INSERT INTO orders (user_id, status, created_at) VALUES
                                                     (1,'PENDING','2025-02-01'), (1,'PAID','2025-02-02'), (2,'PENDING','2025-02-03');
INSERT INTO order_items (order_id, product_id, quantity) VALUES
                                                             (1,100,2),(1,101,1),(2,100,1),(3,102,5);
