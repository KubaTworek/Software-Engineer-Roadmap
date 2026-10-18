package com.example.ecommerce.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import com.example.ecommerce.inventory.InventoryItemRepository;
import com.example.ecommerce.outbox.OutboxEvent;
import com.example.ecommerce.outbox.OutboxEventRepository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Profil infrastrukturalny nie udaje semantyki PostgreSQL przez H2. Ten smoke
 * test weryfikuje rzeczywisty schemat, constraints oraz dialekt wykorzystywany
 * później przez testy współbieżnej rezerwacji i SKIP LOCKED.
 */
@Testcontainers
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "app.inventory.expiration-worker.enabled=false",
        "app.catalog-cache.warmer-enabled=false",
        "app.outbox.publisher-enabled=false"
})
class PostgreSqlInfrastructureIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired InventoryItemRepository inventory;
    @Autowired OutboxEventRepository outbox;
    @Autowired PlatformTransactionManager transactions;

    @Test
    void postgresqlCreatesCriticalConstraints() {
        Integer namedDomainConstraints = jdbc.queryForObject("""
                select count(*)
                from information_schema.table_constraints
                where constraint_type = 'UNIQUE'
                  and constraint_name in (
                    'uk_inventory_reservation_order_variant',
                    'uk_idempotency_key_user_operation')
                """, Integer.class);
        Integer paymentUniqueConstraints = jdbc.queryForObject("""
                select count(*)
                from information_schema.table_constraints
                where table_name = 'payments'
                  and constraint_type = 'UNIQUE'
                """, Integer.class);

        assertThat(namedDomainConstraints).isEqualTo(2);
        // order_id, idempotency_key i provider_event_id muszą być niezależnie
        // unikalne; Hibernate może nazwać constraint relacji OneToOne automatycznie.
        assertThat(paymentUniqueConstraints).isGreaterThanOrEqualTo(3);
    }

    @Test
    void twoConcurrentReservationsCannotSellTheLastUnitTwice() throws Exception {
        String suffix = java.util.UUID.randomUUID().toString();
        Long categoryId = jdbc.queryForObject("""
                insert into categories(name, slug) values (?, ?) returning id
                """, Long.class, "Concurrency", "concurrency-" + suffix);
        Long productId = jdbc.queryForObject("""
                insert into products(category_id, created_at, name, sku, slug, status)
                values (?, now(), ?, ?, ?, 'ACTIVE') returning id
                """, Long.class, categoryId, "Last item", "P-" + suffix, "last-" + suffix);
        Long variantId = jdbc.queryForObject("""
                insert into product_variants(active, currency, price, product_id, name, sku)
                values (true, 'PLN', 10.00, ?, 'Default', ?) returning id
                """, Long.class, productId, "SKU-" + suffix);
        jdbc.update("""
                insert into inventory_items(available_quantity, reserved_quantity, sold_quantity,
                                            updated_at, variant_id, version)
                values (1, 0, 0, now(), ?, 0)
                """, variantId);

        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var attempt = (java.util.concurrent.Callable<Boolean>) () -> {
                start.await(5, TimeUnit.SECONDS);
                return new TransactionTemplate(transactions).execute(status -> {
                    var item = inventory.findByVariantIdForUpdate(variantId).orElseThrow();
                    if (item.sellableQuantity() == 0) {
                        return false;
                    }
                    item.reserve(1);
                    return true;
                });
            };
            var first = pool.submit(attempt);
            var second = pool.submit(attempt);
            start.countDown();

            assertThat(Set.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(jdbc.queryForObject(
                    "select reserved_quantity from inventory_items where variant_id = ?",
                    Integer.class, variantId)).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void skipLockedLetsWorkersClaimDifferentOutboxRows() throws Exception {
        outbox.deleteAll();
        outbox.saveAndFlush(new OutboxEvent("Order", "1", "Created", "{}"));
        outbox.saveAndFlush(new OutboxEvent("Order", "2", "Created", "{}"));

        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch claimed = new CountDownLatch(2);
        try {
            var claim = (java.util.concurrent.Callable<Long>) () ->
                    new TransactionTemplate(transactions).execute(status -> {
                        var now = Instant.now();
                        var event = outbox.claimPublishableBatch(now, now.minusSeconds(60), 1).getFirst();
                        event.claim(now);
                        claimed.countDown();
                        try {
                            if (!claimed.await(5, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("Workers did not claim concurrently");
                            }
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(interrupted);
                        }
                        return event.getId();
                    });
            var first = pool.submit(claim);
            var second = pool.submit(claim);

            assertThat(first.get(10, TimeUnit.SECONDS))
                    .isNotEqualTo(second.get(10, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }
}
