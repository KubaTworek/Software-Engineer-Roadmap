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

@Component
@Profile("performance-seed")
public class PerformanceDatasetGenerator implements ApplicationRunner {
    private static final int BATCH_SIZE = 5_000;

    private final JdbcTemplate jdbcTemplate;
    private final int organizationCount;
    private final int customerCount;
    private final int eventCount;
    private final int reservationCount;

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

    @Override
    public void run(ApplicationArguments args) {
        Long existingEvents = jdbcTemplate.queryForObject("select count(*) from events", Long.class);
        Long existingReservations = jdbcTemplate.queryForObject("select count(*) from reservations", Long.class);
        if ((existingEvents != null && existingEvents >= eventCount) &&
                (existingReservations != null && existingReservations >= reservationCount)) {
            return;
        }

        jdbcTemplate.update("delete from reservations");
        jdbcTemplate.update("delete from capacity_pools");
        jdbcTemplate.update("delete from events");
        jdbcTemplate.update("delete from customers");
        jdbcTemplate.update("delete from app_users");
        jdbcTemplate.update("delete from organizations");

        List<UUID> organizations = generateIds(organizationCount);
        List<UUID> customers = generateIds(customerCount);
        List<UUID> events = generateIds(eventCount);

        insertOrganizations(organizations);
        insertCustomers(customers);
        insertEvents(events, organizations);
        insertCapacityPools(events);
        insertReservations(events, organizations, customers);
    }

    private List<UUID> generateIds(int count) {
        List<UUID> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ids.add(UUID.randomUUID());
        }
        return ids;
    }

    private void insertOrganizations(List<UUID> organizations) {
        batch(organizations.size(), (ps, i) -> {
            ps.setObject(1, organizations.get(i));
            ps.setString(2, "Organization " + i);
            ps.setTimestamp(3, Timestamp.from(Instant.now()));
        }, "insert into organizations(id, name, created_at) values (?, ?, ?)");
    }

    private void insertCustomers(List<UUID> customers) {
        batch(customers.size(), (ps, i) -> {
            ps.setObject(1, customers.get(i));
            ps.setString(2, "Customer " + i);
            ps.setString(3, "customer" + i + "@example.com");
            ps.setTimestamp(4, Timestamp.from(Instant.now()));
        }, "insert into customers(id, full_name, email, created_at) values (?, ?, ?, ?)");
    }

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

    private void insertCapacityPools(List<UUID> events) {
        batch(events.size(), (ps, i) -> {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, events.get(i));
            ps.setInt(3, 1000);
            ps.setInt(4, 1000);
            ps.setLong(5, 0L);
        }, "insert into capacity_pools(id, event_id, total_capacity, available_capacity, version) values (?, ?, ?, ?, ?)");
    }

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

    private void batch(int total, Binder binder, String sql) {
        for (int offset = 0; offset < total; offset += BATCH_SIZE) {
            int from = offset;
            int size = Math.min(BATCH_SIZE, total - offset);
            jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    binder.bind(ps, from + i);
                }

                @Override
                public int getBatchSize() {
                    return size;
                }
            });
        }
    }

    @FunctionalInterface
    private interface Binder {
        void bind(PreparedStatement ps, int i) throws SQLException;
    }
}
