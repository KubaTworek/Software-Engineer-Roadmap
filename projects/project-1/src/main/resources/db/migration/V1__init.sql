-- ============================================================
-- Tabela eventów.
--
-- Przechowuje podstawowe dane wydarzenia:
-- - nazwę,
-- - miasto,
-- - kategorię,
-- - datę rozpoczęcia,
-- - status,
-- - datę utworzenia.
--
-- Na tym etapie event nie ma jeszcze organization_id.
-- Kolumna organization_id zostanie dodana późniejszą migracją.
--
-- Dostępność miejsc nie jest trzymana bezpośrednio w events.
-- Do tego służy osobna tabela capacity_pools.
-- ============================================================
CREATE TABLE events (
                        id UUID PRIMARY KEY,
                        name VARCHAR(255) NOT NULL,
                        city VARCHAR(120) NOT NULL,
                        category VARCHAR(120) NOT NULL,
                        starts_at TIMESTAMPTZ NOT NULL,
                        status VARCHAR(40) NOT NULL,
                        created_at TIMESTAMPTZ NOT NULL
);

-- ============================================================
-- Tabela puli miejsc dla eventu.
--
-- Jeden event ma jedną pulę miejsc.
--
-- event_id:
-- - jest kluczem obcym do events(id),
-- - ma UNIQUE, więc jeden event nie może mieć wielu pul miejsc.
--
-- total_capacity:
-- - całkowita liczba miejsc,
-- - musi być większa od zera.
--
-- available_capacity:
-- - aktualna liczba dostępnych miejsc,
-- - nie może spaść poniżej zera.
--
-- version:
-- - pole używane przez optimistic locking w JPA przez @Version.
-- ============================================================
CREATE TABLE capacity_pools (
                                id UUID PRIMARY KEY,
                                event_id UUID NOT NULL UNIQUE REFERENCES events(id),
                                total_capacity INTEGER NOT NULL CHECK (total_capacity > 0),
                                available_capacity INTEGER NOT NULL CHECK (available_capacity >= 0),
                                version BIGINT NOT NULL
);

-- ============================================================
-- Tabela klientów końcowych.
--
-- Customer reprezentuje osobę dokonującą rezerwacji.
-- To nie jest to samo co AppUser z etapu security.
--
-- email jest unikalny, bo aplikacja wyszukuje klienta po emailu
-- i nie powinna tworzyć wielu rekordów dla tego samego adresu.
-- ============================================================
CREATE TABLE customers (
                           id UUID PRIMARY KEY,
                           full_name VARCHAR(255) NOT NULL,
                           email VARCHAR(255) NOT NULL UNIQUE,
                           created_at TIMESTAMPTZ NOT NULL
);

-- ============================================================
-- Tabela rezerwacji.
--
-- Łączy event z klientem.
--
-- event_id wskazuje, którego eventu dotyczy rezerwacja.
-- customer_id wskazuje klienta, który dokonał rezerwacji.
--
-- status przechowuje stan rezerwacji, np.:
-- - PENDING,
-- - CONFIRMED,
-- - CANCELLED,
-- - PAYMENT_TIMEOUT.
--
-- confirmed_at i cancelled_at są opcjonalne, bo zależą od statusu.
--
-- Na tym etapie tabela nie ma jeszcze organization_id.
-- Kolumna organization_id zostanie dodana późniejszą migracją
-- jako denormalizacja pod zapytania po organizacji.
-- ============================================================
CREATE TABLE reservations (
                              id UUID PRIMARY KEY,
                              event_id UUID NOT NULL REFERENCES events(id),
                              customer_id UUID NOT NULL REFERENCES customers(id),
                              status VARCHAR(40) NOT NULL,
                              created_at TIMESTAMPTZ NOT NULL,
                              confirmed_at TIMESTAMPTZ,
                              cancelled_at TIMESTAMPTZ
);

-- ============================================================
-- Tabela organizacji.
--
-- Organization reprezentuje tenant/organizację w systemie.
--
-- Na tym etapie tabela istnieje, ale events nie ma jeszcze organization_id.
-- Powiązanie eventów z organizacjami zostanie dodane późniejszym ALTER TABLE.
--
-- name jest unikalne w tym prostym modelu edukacyjnym.
-- ============================================================
CREATE TABLE organizations (
                               id UUID PRIMARY KEY,
                               name VARCHAR(255) NOT NULL UNIQUE,
                               created_at TIMESTAMPTZ NOT NULL
);

-- ============================================================
-- Tabela użytkowników aplikacji.
--
-- AppUser reprezentuje konto używane do security:
-- - logowania,
-- - ról,
-- - autoryzacji,
-- - tenant boundary.
--
-- organization_id wskazuje organizację użytkownika.
-- Może być NULL, jeżeli użytkownik nie jest przypisany do organizacji.
--
-- Na tym etapie tabela nie ma jeszcze password_hash.
-- Kolumna password_hash zostanie dodana późniejszą migracją security.
-- ============================================================
CREATE TABLE app_users (
                           id UUID PRIMARY KEY,
                           organization_id UUID REFERENCES organizations(id),
                           email VARCHAR(255) NOT NULL UNIQUE,
                           role VARCHAR(60) NOT NULL,
                           created_at TIMESTAMPTZ NOT NULL
);

-- ============================================================
-- Indeks pod wyszukiwanie eventów.
--
-- Wspiera access pattern:
--
-- GET /events?city=Warsaw&from=2026-06-01&category=music
--
-- Kolejność kolumn:
-- - city: filtr równościowy,
-- - category: filtr równościowy,
-- - starts_at: filtr zakresowy i sortowanie po czasie.
-- ============================================================
CREATE INDEX idx_events_city_category_starts_at
    ON events(city, category, starts_at);

-- ============================================================
-- Indeks pod rezerwacje konkretnego eventu.
--
-- Wspiera zapytania:
-- - rezerwacje dla eventu,
-- - statystyki po statusie,
-- - sortowanie po dacie utworzenia.
--
-- Kolejność:
-- - event_id: zawęża do jednego eventu,
-- - status: pozwala filtrować lub grupować po statusie,
-- - created_at DESC: wspiera sortowanie od najnowszych.
-- ============================================================
CREATE INDEX idx_reservations_event_status_created_at
    ON reservations(event_id, status, created_at DESC);

-- ============================================================
-- Indeks pod rezerwacje klienta.
--
-- Wspiera endpoint:
--
-- GET /customers/{customerId}/reservations
--
-- customer_id zawęża do jednego klienta,
-- created_at DESC pozwala szybko zwracać najnowsze rezerwacje.
--
-- Późniejsza migracja dodaje pełniejszy indeks z id DESC,
-- lepszy pod stabilną keyset pagination.
-- ============================================================
CREATE INDEX idx_reservations_customer_created_at
    ON reservations(customer_id, created_at DESC);

-- ============================================================
-- Tabela audytu.
--
-- AuditLog przechowuje techniczne lub biznesowe wpisy audytowe,
-- np. potwierdzenie rezerwacji albo timeout płatności.
--
-- reservation_id jest przechowywane jako UUID, bez relacji FK.
-- To upraszcza zapis audytu i luźniej wiąże audyt z modelem domenowym.
-- ============================================================
CREATE TABLE audit_logs (
                            id UUID PRIMARY KEY,
                            reservation_id UUID NOT NULL,
                            type VARCHAR(80) NOT NULL,
                            message VARCHAR(1000) NOT NULL,
                            created_at TIMESTAMPTZ NOT NULL
);

-- ============================================================
-- Tabela komunikatów wychodzących.
--
-- Służy do zapisywania prób side-effectów asynchronicznych:
-- - email,
-- - webhook,
-- - notyfikacja zewnętrzna.
--
-- To nie jest pełny outbox pattern.
-- To prosty techniczny zapis tego, czy wysyłka się udała,
-- jaki był kanał, payload i ewentualny błąd.
-- ============================================================
CREATE TABLE outbound_messages (
                                   id UUID PRIMARY KEY,
                                   reservation_id UUID NOT NULL,
                                   channel VARCHAR(80) NOT NULL,
                                   status VARCHAR(40) NOT NULL,
                                   payload VARCHAR(1000) NOT NULL,
                                   error_message VARCHAR(1000),
                                   created_at TIMESTAMPTZ NOT NULL
);

-- ============================================================
-- Indeks po reservation_id w audycie.
--
-- Przydatny do szybkiego znalezienia wpisów audytowych dla jednej rezerwacji.
-- ============================================================
CREATE INDEX idx_audit_logs_reservation_id
    ON audit_logs(reservation_id);

-- ============================================================
-- Indeks po reservation_id w outbound_messages.
--
-- Przydatny do sprawdzenia, jakie komunikaty wychodzące zostały zapisane
-- dla konkretnej rezerwacji.
-- ============================================================
CREATE INDEX idx_outbound_messages_reservation_id
    ON outbound_messages(reservation_id);