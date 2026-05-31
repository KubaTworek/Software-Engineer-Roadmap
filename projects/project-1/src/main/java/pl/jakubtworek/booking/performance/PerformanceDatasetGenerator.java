package pl.jakubtworek.booking.performance;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Generator dużego datasetu do etapu SQL/performance.
 *
 * Klasa uruchamia się tylko przy aktywnym profilu:
 *
 * performance-seed
 *
 * Dzięki temu zwykłe uruchomienie aplikacji i zwykłe testy nie próbują tworzyć
 * 100 000 eventów oraz 1 000 000 rezerwacji.
 *
 * Celem tej klasy nie jest elegancki model domenowy, tylko szybkie zasypanie bazy
 * dużą ilością danych, żeby można było analizować:
 *
 * - indeksy,
 * - EXPLAIN ANALYZE,
 * - offset pagination,
 * - keyset pagination,
 * - agregacje,
 * - koszt joinów,
 * - N+1,
 * - read-heavy workload.
 */
@Component
@Profile("performance-seed")
public class PerformanceDatasetGenerator implements ApplicationRunner {

    /**
     * Rozmiar batcha dla insertów.
     *
     * Zamiast wykonywać milion pojedynczych INSERT-ów, wykonujemy je paczkami.
     * To znacząco zmniejsza narzut komunikacji z bazą.
     *
     * 5 000 jest rozsądną wartością edukacyjną, ale w realnym środowisku warto
     * sprawdzić różne rozmiary batchy.
     */
    private static final int BATCH_SIZE = 5_000;

    /**
     * JdbcTemplate jest używany celowo zamiast JPA.
     *
     * Przy generowaniu dużego datasetu JPA byłoby wolniejsze i cięższe:
     * - persistence context trzymałby dużo encji,
     * - dirty checking byłby niepotrzebny,
     * - trzeba byłoby ręcznie flushować i czyścić kontekst.
     *
     * Do masowego seedingowania prosty SQL przez JdbcTemplate jest lepszym wyborem.
     */
    private final JdbcTemplate jdbcTemplate;

    /**
     * Liczba organizacji do wygenerowania.
     *
     * Można ją nadpisać przez property:
     *
     * performance.seed.organizations
     */
    private final int organizationCount;

    /**
     * Liczba klientów do wygenerowania.
     *
     * Domyślnie 10 000.
     */
    private final int customerCount;

    /**
     * Liczba eventów do wygenerowania.
     *
     * Domyślnie 100 000.
     */
    private final int eventCount;

    /**
     * Liczba rezerwacji do wygenerowania.
     *
     * Domyślnie 1 000 000.
     */
    private final int reservationCount;

    /**
     * Konstruktor z konfiguracją przez @Value.
     *
     * Domyślne wartości są duże, ale można je zmniejszyć lokalnie, np.:
     *
     * -Dperformance.seed.events=10000
     * -Dperformance.seed.reservations=100000
     */
    public PerformanceDatasetGenerator(
            JdbcTemplate jdbcTemplate,
            @Value("${performance.seed.organizations:100}") int organizationCount,
            @Value("${performance.seed.customers:10000}") int customerCount,
            @Value("${performance.seed.events:100000}") int eventCount,
            @Value("${performance.seed.reservations:1000000}") int reservationCount
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.organizationCount = organizationCount;
        this.customerCount = customerCount;
        this.eventCount = eventCount;
        this.reservationCount = reservationCount;
    }

    /**
     * Metoda uruchamiana automatycznie po starcie aplikacji.
     *
     * ApplicationRunner jest wygodny do jednorazowych zadań inicjalizacyjnych.
     *
     * Tutaj:
     * - sprawdzamy, czy dataset już istnieje,
     * - jeśli istnieje w wystarczającej liczbie, nic nie robimy,
     * - jeśli nie, czyścimy tabele i generujemy dane od nowa.
     */
    @Override
    public void run(ApplicationArguments args) {
        /*
         * Prosty guard przed ponownym generowaniem danych.
         *
         * Jeśli baza ma już co najmniej oczekiwaną liczbę eventów i rezerwacji,
         * generator kończy działanie.
         */
        Long existingEvents = jdbcTemplate.queryForObject("select count(*) from events", Long.class);
        Long existingReservations = jdbcTemplate.queryForObject("select count(*) from reservations", Long.class);

        if ((existingEvents != null && existingEvents >= eventCount) &&
                (existingReservations != null && existingReservations >= reservationCount)) {
            return;
        }

        /*
         * Czyścimy tabele w kolejności zależności FK.
         *
         * Najpierw usuwamy tabele zależne, potem nadrzędne:
         *
         * reservations -> capacity_pools -> events -> customers/app_users -> organizations
         *
         * Inna kolejność mogłaby spowodować naruszenie constraintów foreign key.
         */
        jdbcTemplate.update("delete from reservations");
        jdbcTemplate.update("delete from capacity_pools");
        jdbcTemplate.update("delete from events");
        jdbcTemplate.update("delete from customers");
        jdbcTemplate.update("delete from app_users");
        jdbcTemplate.update("delete from organizations");

        /*
         * Generujemy wcześniej UUID-y, żeby łatwo powiązać dane między tabelami.
         *
         * Dzięki temu rezerwacje mogą wskazywać na istniejące eventy, klientów
         * i organizacje bez dodatkowych SELECT-ów.
         */
        List<UUID> organizations = generateIds(organizationCount);
        List<UUID> customers = generateIds(customerCount);
        List<UUID> events = generateIds(eventCount);

        /*
         * Kolejność insertów odpowiada zależnościom:
         *
         * organizations -> customers -> events -> capacity_pools -> reservations
         */
        insertOrganizations(organizations);
        insertCustomers(customers);
        insertEvents(events, organizations);
        insertCapacityPools(events);
        insertReservations(events, organizations, customers);
    }

    /**
     * Generuje listę UUID-ów.
     *
     * Lista jest trzymana w pamięci, ponieważ później używamy tych ID przy
     * tworzeniu relacji między rekordami.
     */
    private List<UUID> generateIds(int count) {
        List<UUID> ids = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            ids.add(UUID.randomUUID());
        }

        return ids;
    }

    /**
     * Wstawia organizacje.
     *
     * Dane są syntetyczne. Ich celem nie jest realizm biznesowy, tylko zapewnienie
     * rozkładu danych do testowania zapytań po organization_id.
     */
    private void insertOrganizations(List<UUID> organizations) {
        batch(organizations.size(), (ps, i) -> {
            ps.setObject(1, organizations.get(i));
            ps.setString(2, "Organization " + i);
            ps.setTimestamp(3, Timestamp.from(Instant.now()));
        }, "insert into organizations(id, name, created_at) values (?, ?, ?)");
    }

    /**
     * Wstawia klientów.
     *
     * Email jest deterministyczny:
     *
     * customer0@example.com
     * customer1@example.com
     * ...
     *
     * Dzięki temu łatwo debugować dane testowe.
     */
    private void insertCustomers(List<UUID> customers) {
        batch(customers.size(), (ps, i) -> {
            ps.setObject(1, customers.get(i));
            ps.setString(2, "Customer " + i);
            ps.setString(3, "customer" + i + "@example.com");
            ps.setTimestamp(4, Timestamp.from(Instant.now()));
        }, "insert into customers(id, full_name, email, created_at) values (?, ?, ?, ?)");
    }

    /**
     * Wstawia eventy.
     *
     * Dane są rozłożone cyklicznie po:
     *
     * - miastach,
     * - kategoriach,
     * - organizacjach,
     * - datach.
     *
     * To daje powtarzalny dataset do zapytań typu:
     *
     * city = Warsaw
     * category = music
     * starts_at >= ...
     *
     * Ten rozkład nie jest idealnie realistyczny, ale jest wystarczający do nauki
     * indeksów, selectivity i planów wykonania.
     */
    private void insertEvents(List<UUID> events, List<UUID> organizations) {
        String[] cities = {"Warsaw", "Krakow", "Gdansk", "Poznan", "Wroclaw"};
        String[] categories = {"music", "sport", "tech", "business", "theatre"};
        Instant base = Instant.parse("2026-01-01T10:00:00Z");

        batch(events.size(), (ps, i) -> {
            ps.setObject(1, events.get(i));
            ps.setObject(2, organizations.get(i % organizations.size()));
            ps.setString(3, "Event " + i);
            ps.setString(4, cities[i % cities.length]);
            ps.setString(5, categories[i % categories.length]);
            ps.setTimestamp(6, Timestamp.from(base.plus(i % 365, ChronoUnit.DAYS)));
            ps.setString(7, "PUBLISHED");
            ps.setTimestamp(8, Timestamp.from(Instant.now()));
        }, "insert into events(id, organization_id, name, city, category, starts_at, status, created_at) values (?, ?, ?, ?, ?, ?, ?, ?)");
    }

    /**
     * Wstawia pule dostępności dla eventów.
     *
     * Każdy event dostaje jedną pulę miejsc:
     *
     * - total_capacity = 1000,
     * - available_capacity = 1000,
     * - version = 0.
     *
     * Version jest potrzebne do optimistic lockingu w etapach concurrency.
     */
    private void insertCapacityPools(List<UUID> events) {
        batch(events.size(), (ps, i) -> {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, events.get(i));
            ps.setInt(3, 1000);
            ps.setInt(4, 1000);
            ps.setLong(5, 0L);
        }, "insert into capacity_pools(id, event_id, total_capacity, available_capacity, version) values (?, ?, ?, ?, ?)");
    }

    /**
     * Wstawia rezerwacje.
     *
     * Rezerwacje są rozłożone cyklicznie po:
     *
     * - eventach,
     * - organizacjach,
     * - klientach,
     * - statusach.
     *
     * Dzięki temu można testować:
     *
     * - zapytania po event_id,
     * - zapytania po organization_id + status,
     * - zapytania po customer_id,
     * - agregacje po statusie.
     */
    private void insertReservations(List<UUID> events, List<UUID> organizations, List<UUID> customers) {
        String[] statuses = {"PENDING", "CONFIRMED", "CANCELLED", "PAYMENT_TIMEOUT"};
        Instant base = Instant.parse("2026-01-01T00:00:00Z");

        batch(reservationCount, (ps, i) -> {
            int eventIndex = i % events.size();
            String status = statuses[i % statuses.length];
            Instant createdAt = base.plus(i, ChronoUnit.SECONDS);

            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, events.get(eventIndex));
            ps.setObject(3, organizations.get(eventIndex % organizations.size()));
            ps.setObject(4, customers.get(i % customers.size()));
            ps.setString(5, status);
            ps.setTimestamp(6, Timestamp.from(createdAt));
            ps.setTimestamp(7, "CONFIRMED".equals(status) ? Timestamp.from(createdAt.plus(1, ChronoUnit.MINUTES)) : null);
            ps.setTimestamp(8, "CANCELLED".equals(status) ? Timestamp.from(createdAt.plus(2, ChronoUnit.MINUTES)) : null);
        }, "insert into reservations(id, event_id, organization_id, customer_id, status, created_at, confirmed_at, cancelled_at) values (?, ?, ?, ?, ?, ?, ?, ?)");
    }

    /**
     * Wspólna metoda do batch insertów.
     *
     * Dzieli duży insert na paczki po BATCH_SIZE.
     *
     * Przykład:
     * - total = 1 000 000,
     * - BATCH_SIZE = 5 000,
     * - liczba batchy = 200.
     *
     * Dzięki temu nie wykonujemy miliona osobnych round-tripów do bazy.
     */
    private void batch(int total, Binder binder, String sql) {
        for (int offset = 0; offset < total; offset += BATCH_SIZE) {
            int from = offset;
            int size = Math.min(BATCH_SIZE, total - offset);

            jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {

                /**
                 * Ustawia wartości dla jednego rekordu w batchu.
                 *
                 * i jest indeksem wewnątrz aktualnej paczki,
                 * a from + i daje globalny indeks rekordu.
                 */
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    binder.bind(ps, from + i);
                }

                /**
                 * Informuje JdbcTemplate, ile rekordów jest w aktualnej paczce.
                 */
                @Override
                public int getBatchSize() {
                    return size;
                }
            });
        }
    }

    /**
     * Funkcyjny interfejs pomocniczy.
     *
     * Pozwala przekazać logikę wypełniania PreparedStatement dla różnych tabel.
     */
    @FunctionalInterface
    private interface Binder {

        /**
         * Ustawia parametry PreparedStatement dla rekordu o indeksie i.
         */
        void bind(PreparedStatement ps, int i) throws SQLException;
    }
}