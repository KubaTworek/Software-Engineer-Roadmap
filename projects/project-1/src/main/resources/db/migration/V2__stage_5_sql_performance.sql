-- Dodaje powiązanie eventu z organizacją.
--
-- Ta kolumna jest potrzebna po wprowadzeniu tenantów i autoryzacji opartej
-- o organizację.
--
-- Dzięki niej można sprawdzić m.in.:
-- - czy EVENT_MANAGER zarządza eventem swojej organizacji,
-- - czy ORG_ADMIN działa w swoim tenantcie,
-- - do jakiej organizacji należy event.
ALTER TABLE events
    ADD COLUMN organization_id UUID REFERENCES organizations(id);

-- Indeks pod wyszukiwanie eventów po access patternie:
--
-- GET /events?city=Warsaw&from=2026-06-01&category=music
--
-- Kolejność kolumn:
-- - city: filtr równościowy,
-- - category: filtr równościowy,
-- - starts_at: filtr zakresowy i potencjalne sortowanie.
--
-- IF NOT EXISTS pozwala uruchomić skrypt ponownie bez błędu,
-- jeśli indeks już istnieje.
CREATE INDEX IF NOT EXISTS idx_event_city_category_start_time
    ON events(city, category, starts_at);

-- Indeks pod pobieranie eventów konkretnej organizacji.
--
-- Przydatny dla widoków managera albo admina organizacji, np.:
-- "pokaż najnowsze/nadchodzące eventy mojej organizacji".
--
-- organization_id jest filtrem tenantowym,
-- starts_at DESC wspiera sortowanie od najpóźniejszych/najnowszych dat.
CREATE INDEX IF NOT EXISTS idx_event_organization_starts_at
    ON events(organization_id, starts_at DESC);

-- Dodaje organization_id bezpośrednio do reservations.
--
-- To jest denormalizacja względem relacji:
--
-- reservation -> event -> organization
--
-- Dzięki temu zapytania po rezerwacjach organizacji nie muszą za każdym razem
-- przechodzić przez join do events.
--
-- Jest to szczególnie ważne dla endpointu:
-- GET /organizations/{id}/reservations?status=CONFIRMED&page=...
ALTER TABLE reservations
    ADD COLUMN organization_id UUID REFERENCES organizations(id);

-- Uzupełnia organization_id w istniejących rezerwacjach na podstawie eventów.
--
-- Dla każdej rezerwacji szukamy jej eventu i kopiujemy organization_id z events.
--
-- Warunek e.organization_id IS NOT NULL chroni przed ustawianiem wartości NULL
-- tam, gdzie event nie ma przypisanej organizacji.
--
-- To jest typowy krok migracyjny po dodaniu denormalizowanej kolumny.
UPDATE reservations r
SET organization_id = e.organization_id
    FROM events e
WHERE r.event_id = e.id
  AND e.organization_id IS NOT NULL;

-- Indeks pod główny access pattern z etapu SQL/performance:
--
-- GET /organizations/{id}/reservations?status=CONFIRMED&page=...
--
-- Kolejność kolumn:
-- - organization_id: zawęża dane do jednego tenant/organizacji,
-- - status: filtr biznesowy,
-- - created_at DESC: sortowanie najnowszych rezerwacji.
--
-- Ten indeks powinien ograniczyć skanowanie dużej tabeli reservations
-- przy listowaniu rezerwacji organizacji.
CREATE INDEX IF NOT EXISTS idx_reservation_org_status_created_at
    ON reservations(organization_id, status, created_at DESC);

-- Indeks pod rezerwacje klienta i keyset pagination.
--
-- Access pattern:
-- GET /customers/{id}/reservations
-- GET /customers/{id}/reservations/keyset
--
-- Kolejność:
-- - customer_id: filtr po kliencie,
-- - created_at DESC: sortowanie od najnowszych,
-- - id DESC: stabilny tie-breaker dla keyset pagination.
--
-- id jest ważne, bo wiele rekordów może mieć ten sam created_at.
-- Pełny cursor keyset powinien wtedy używać created_at oraz id.
CREATE INDEX IF NOT EXISTS idx_reservation_customer_created_at_id
    ON reservations(customer_id, created_at DESC, id DESC);

-- Indeks pod pobieranie rezerwacji eventu.
--
-- Przydatny dla endpointów typu:
-- - /api/events/{eventId}/reservations/n-plus-one
-- - /api/events/{eventId}/reservations/fetch-join
-- - /api/events/{eventId}/reservations/entity-graph
--
-- event_id zawęża do jednego eventu,
-- created_at DESC wspiera sortowanie najnowszych rezerwacji.
CREATE INDEX IF NOT EXISTS idx_reservation_event_created_at
    ON reservations(event_id, created_at DESC);