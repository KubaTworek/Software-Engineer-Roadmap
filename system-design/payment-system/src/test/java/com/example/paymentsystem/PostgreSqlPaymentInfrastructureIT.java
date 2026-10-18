package com.example.paymentsystem;

import com.example.paymentsystem.merchant.Merchant;
import com.example.paymentsystem.merchant.MerchantRepository;
import com.example.paymentsystem.payment.*;
import com.example.paymentsystem.payout.PayoutService;
import com.example.paymentsystem.idempotency.IdempotencyClaim;
import com.example.paymentsystem.idempotency.IdempotencyService;
import com.example.paymentsystem.outbox.OutboxEvent;
import com.example.paymentsystem.outbox.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** PostgreSQL is required because H2 cannot prove row-lock and migration semantics. */
@SpringBootTest
@ActiveProfiles("infrastructure-tests")
@Testcontainers(disabledWithoutDocker = true)
class PostgreSqlPaymentInfrastructureIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @Autowired MerchantRepository merchants;
    @Autowired PaymentService payments;
    @Autowired PayoutService payouts;
    @Autowired IdempotencyService idempotency;
    @Autowired OutboxEventRepository outbox;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void merchantLockPreventsTwoPayoutsOfTheSameBalance() throws Exception {
        Merchant merchant = merchants.save(new Merchant("Concurrent payout", "PLN"));
        PaymentResponse payment = payments.create(new CreatePaymentRequest(merchant.getMerchantId(), "order", null,
                10_000, "PLN", CaptureMode.AUTOMATIC, "PL", "PL"), "pg-payment");
        payments.markSucceeded(payment.paymentId());

        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> attemptPayout(start, merchant, "payout-a"));
            var second = executor.submit(() -> attemptPayout(start, merchant, "payout-b"));
            start.countDown();

            assertThat(List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("PAID", "REJECTED");
        }
    }

    @Test
    void databaseUniqueKeyCoordinatesIdempotencyAcrossConcurrentInstances() throws Exception {
        String key = "postgres-race-" + UUID.randomUUID();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> { start.await(); return idempotency.claim(key, "infra", "body").outcome(); });
            var second = executor.submit(() -> { start.await(); return idempotency.claim(key, "infra", "body").outcome(); });
            start.countDown();
            assertThat(List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(
                    IdempotencyClaim.Outcome.ACQUIRED, IdempotencyClaim.Outcome.IN_PROGRESS);
        }
    }

    @Test
    void skipLockedLetsSecondOutboxWorkerContinueWithoutPublishingTheLockedEvent() throws Exception {
        outbox.deleteAll();
        outbox.saveAndFlush(new OutboxEvent("Payment", UUID.randomUUID(), "PaymentSucceeded", "{}"));
        CountDownLatch firstWorkerLocked = new CountDownLatch(1);
        CountDownLatch releaseFirstWorker = new CountDownLatch(1);
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> transactions.execute(status -> {
                int claimed = outbox.lockPublishableBatch(Instant.now()).size();
                firstWorkerLocked.countDown();
                await(releaseFirstWorker);
                return claimed;
            }));
            assertThat(firstWorkerLocked.await(5, TimeUnit.SECONDS))
                    .as("first worker should acquire the row lock")
                    .isTrue();
            var second = executor.submit(() -> transactions.execute(status ->
                    outbox.lockPublishableBatch(Instant.now()).size()));

            try {
                // SKIP LOCKED is useful only if another worker makes progress immediately.
                // A bounded wait turns an accidental blocking query into a quick, diagnostic CI failure.
                assertThat(second.get(5, TimeUnit.SECONDS)).isZero();
            } finally {
                releaseFirstWorker.countDown();
            }
            assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo(1);
        }
    }

    private String attemptPayout(CountDownLatch start, Merchant merchant, String key) throws InterruptedException {
        start.await();
        try {
            return payouts.create(merchant.getMerchantId(), "PLN", key).getStatus().name();
        } catch (IllegalStateException noBalanceAfterWinner) {
            return "REJECTED";
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Test worker interrupted", interrupted);
        }
    }
}
