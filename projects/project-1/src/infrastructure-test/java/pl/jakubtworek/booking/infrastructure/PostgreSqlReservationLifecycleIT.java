package pl.jakubtworek.booking.infrastructure;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.RollbackException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import pl.jakubtworek.booking.entity.Reservation;
import pl.jakubtworek.booking.repository.CustomerRepository;
import pl.jakubtworek.booking.repository.ReservationRepository;
import pl.jakubtworek.booking.service.CustomerRegistrationService;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ten test nie zastępuje szybkiej suite H2. Dowodzi wyłącznie kontraktów, które
 * zależą od PostgreSQL: wykonania migracji, constraintów oraz składni częściowego
 * indeksu i {@code FOR UPDATE SKIP LOCKED}. Uruchomienie wymaga profilu
 * {@code infrastructure-tests}, dzięki czemu zwykły build nie wymaga Dockera.
 */
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration,"
                + "org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration,"
                + "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration",
        "security.jwt.secret=infrastructure-test-secret-at-least-thirty-two-characters"
})
@Testcontainers(disabledWithoutDocker = true)
class PostgreSqlReservationLifecycleIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired ReservationRepository reservations;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired CustomerRepository customers;
    @Autowired CustomerRegistrationService customerRegistrationService;
    @Autowired pl.jakubtworek.booking.service.security.AuthService auth;
    @Autowired pl.jakubtworek.booking.repository.AppUserRepository users;
    @Autowired pl.jakubtworek.booking.security.PasswordHasher passwords;

    @Test void jdbcRegistrationPersistsCanonicalUnicodeNameWithoutChangingEmail() {
        String email="Case-"+UUID.randomUUID()+"@example.test";
        var customer=customerRegistrationService.resolve(" Jose\u0301 ",email);
        assertThat(customer.getFullName()).isEqualTo("José");
        assertThat(jdbc.queryForObject("select full_name from customers where id=?",String.class,customer.getId())).isEqualTo("José");
        assertThat(customer.getEmail()).isEqualTo(email);
    }

    @Test @org.junit.jupiter.api.Timeout(30)
    void concurrentRefreshCommitsExactlyOneSuccessor() throws Exception {
        String email = "refresh-" + UUID.randomUUID() + "@test.local";
        var user = users.save(new pl.jakubtworek.booking.entity.AppUser(null, email,
                passwords.encode("secret"), pl.jakubtworek.booking.entity.UserRole.CUSTOMER));
        String token = auth.login(email, "secret").refreshToken();
        var ready = new java.util.concurrent.CountDownLatch(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Boolean> rotate = () -> {
                ready.countDown();
                if (!ready.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("Missing contender");
                try { auth.refresh(token); return true; }
                catch (pl.jakubtworek.booking.exception.BusinessRuleException rejected) { return false; }
            };
            var a = pool.submit(rotate); var b = pool.submit(rotate);
            assertThat(java.util.List.of(a.get(), b.get())).containsExactlyInAnyOrder(true, false);
        }
        assertThat(jdbc.queryForObject("select count(*) from refresh_tokens where user_id=? and revoked_at is null",
                Integer.class, user.getId())).isEqualTo(1);
    }

    @BeforeEach
    void cleanDomainTables() {
        jdbc.update("delete from reservations");
        jdbc.update("delete from capacity_pools");
        jdbc.update("delete from customers");
        jdbc.update("delete from events");
    }

    @Test
    void migrationProtectsCapacityAndExpirationQueryRunsOnPostgres() {
        UUID eventId = UUID.randomUUID();
        UUID poolId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        Instant now = Instant.parse("2026-06-01T10:00:00Z");

        jdbc.update("""
                insert into events(id, name, city, category, starts_at, status, created_at)
                values (?, 'PostgreSQL lab', 'Warsaw', 'education', ?, 'PUBLISHED', ?)
                """, eventId, Timestamp.from(now.plusSeconds(3600)), Timestamp.from(now));
        jdbc.update("""
                insert into capacity_pools(id, event_id, total_capacity, available_capacity, version)
                values (?, ?, 1, 1, 0)
                """, poolId, eventId);
        jdbc.update("""
                insert into customers(id, full_name, email, created_at)
                values (?, 'Infrastructure Test', ?, ?)
                """, customerId, customerId + "@example.test", Timestamp.from(now));
        jdbc.update("""
                insert into reservations(
                    id, event_id, customer_id, status, created_at, expires_at, version
                ) values (?, ?, ?, 'PENDING', ?, ?, 0)
                """, reservationId, eventId, customerId, Timestamp.from(now), Timestamp.from(now.plusSeconds(60)));

        assertThat(reservations.findExpirableIdsForUpdateSkipLocked(now.plusSeconds(61), 10))
                .containsExactly(reservationId);

        assertThatThrownBy(() -> jdbc.update(
                "update capacity_pools set available_capacity = 2 where id = ?", poolId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void staleOwnerCannotOverwriteConcurrentReservationTransition() {
        Instant createdAt = Instant.parse("2026-06-01T10:00:00Z");
        UUID reservationId = insertPendingReservation(createdAt, createdAt.plusSeconds(60));

        var confirmingEntityManager = entityManagerFactory.createEntityManager();
        var expiringEntityManager = entityManagerFactory.createEntityManager();
        confirmingEntityManager.getTransaction().begin();
        expiringEntityManager.getTransaction().begin();

        Reservation confirmingCopy = confirmingEntityManager.find(Reservation.class, reservationId);
        Reservation expiringCopy = expiringEntityManager.find(Reservation.class, reservationId);

        // Obie decyzje były lokalnie poprawne w chwili odczytu, lecz tylko jeden
        // właściciel wersji może zatwierdzić przejście. Sam końcowy status bez
        // kontroli wersji nie ujawniłby utraconej aktualizacji.
        confirmingCopy.confirm(createdAt.plusSeconds(59));
        expiringCopy.expire(createdAt.plusSeconds(60));
        confirmingEntityManager.getTransaction().commit();

        assertThatThrownBy(() -> expiringEntityManager.getTransaction().commit())
                .isInstanceOf(RollbackException.class);

        confirmingEntityManager.close();
        expiringEntityManager.close();
    }

    @Test
    void twoWorkersSkipRowsAlreadyLockedByAnotherExpirationBatch() {
        Instant createdAt = Instant.parse("2026-06-02T10:00:00Z");
        UUID firstReservation = insertPendingReservation(createdAt, createdAt.plusSeconds(10));
        UUID secondReservation = insertPendingReservation(createdAt.plusMillis(1), createdAt.plusSeconds(11));

        var firstWorker = entityManagerFactory.createEntityManager();
        var secondWorker = entityManagerFactory.createEntityManager();
        firstWorker.getTransaction().begin();
        secondWorker.getTransaction().begin();

        UUID claimedByFirst = claimOneExpired(firstWorker, createdAt.plusSeconds(20));
        UUID claimedBySecond = claimOneExpired(secondWorker, createdAt.plusSeconds(20));

        assertThat(claimedByFirst).isIn(firstReservation, secondReservation);
        assertThat(claimedBySecond).isIn(firstReservation, secondReservation).isNotEqualTo(claimedByFirst);

        firstWorker.getTransaction().rollback();
        secondWorker.getTransaction().rollback();
        firstWorker.close();
        secondWorker.close();
    }

    @Test
    void concurrentFindOrCreateConvergesOnOneCustomer() throws Exception {
        var executor = Executors.newFixedThreadPool(8);
        try {
            var calls = java.util.stream.IntStream.range(0, 8)
                    .<java.util.concurrent.Callable<UUID>>mapToObj(index -> () ->
                            customerRegistrationService.resolve(
                                    "Concurrent Customer " + index,
                                    "one-customer@example.test"
                            ).getId())
                    .toList();

            var ids = executor.invokeAll(calls).stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception failure) {
                            throw new AssertionError(failure);
                        }
                    })
                    .toList();

            assertThat(ids).containsOnly(ids.getFirst());
            assertThat(customers.count()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private UUID insertPendingReservation(Instant createdAt, Instant expiresAt) {
        UUID eventId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        jdbc.update("""
                insert into events(id, name, city, category, starts_at, status, created_at)
                values (?, 'Lifecycle race', 'Warsaw', 'education', ?, 'PUBLISHED', ?)
                """, eventId, Timestamp.from(createdAt.plusSeconds(3600)), Timestamp.from(createdAt));
        jdbc.update("""
                insert into capacity_pools(id, event_id, total_capacity, available_capacity, version)
                values (?, ?, 1, 0, 0)
                """, UUID.randomUUID(), eventId);
        jdbc.update("""
                insert into customers(id, full_name, email, created_at)
                values (?, 'Lifecycle Customer', ?, ?)
                """, customerId, customerId + "@example.test", Timestamp.from(createdAt));
        jdbc.update("""
                insert into reservations(id, event_id, customer_id, status, created_at, expires_at, version)
                values (?, ?, ?, 'PENDING', ?, ?, 0)
                """, reservationId, eventId, customerId, Timestamp.from(createdAt), Timestamp.from(expiresAt));
        return reservationId;
    }

    private UUID claimOneExpired(jakarta.persistence.EntityManager entityManager, Instant now) {
        return (UUID) entityManager.createNativeQuery("""
                SELECT id FROM reservations
                WHERE status IN ('PENDING', 'PAYMENT_TIMEOUT') AND expires_at <= :now
                ORDER BY expires_at, id
                LIMIT 1
                FOR UPDATE SKIP LOCKED
                """)
                .setParameter("now", now)
                .getSingleResult();
    }
}
