package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.overload;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.resilience.CircuitBreaker;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.resilience.TimeoutExecutor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OverloadProtectedRemoteClientTest {

    @Test
    void sharedRetryBudgetShouldStopASecondaryRetryStorm() throws Exception {
        int logicalRequests = 20;
        int maximumAttempts = 3;

        StormResult unprotected = runFailureStorm(logicalRequests, maximumAttempts, 40);
        StormResult protectedByBudget = runFailureStorm(logicalRequests, maximumAttempts, 5);

        assertThat(unprotected.physicalAttempts()).isEqualTo(60);
        assertThat(protectedByBudget.physicalAttempts()).isEqualTo(25);
        assertThat(protectedByBudget.budget().consumed()).isEqualTo(5);
        assertThat(protectedByBudget.budget().denied()).isGreaterThan(0);
    }

    @Test
    void separateBulkheadsShouldIsolateAFullPaymentDependencyFromCatalog() throws Exception {
        CountDownLatch paymentStarted = new CountDownLatch(1);
        CountDownLatch releasePayment = new CountDownLatch(1);
        ExecutorService dependencyThreads = Executors.newFixedThreadPool(4);
        ExecutorService callers = Executors.newSingleThreadExecutor();

        try (TimeoutExecutor timeoutExecutor = new TimeoutExecutor(dependencyThreads)) {
            OverloadProtectedRemoteClient payment = client("payment", 1, timeoutExecutor, new RetryBudget(0));
            OverloadProtectedRemoteClient catalog = client("catalog", 1, timeoutExecutor, new RetryBudget(0));
            RetryPolicy noRetry = new RetryPolicy(1, Duration.ofSeconds(1), Duration.ofMillis(50));

            Future<String> activePayment = callers.submit(() -> payment.execute(
                    RequestDeadline.after(Duration.ofSeconds(2), Clock.systemUTC()),
                    noRetry,
                    context -> {
                        paymentStarted.countDown();
                        releasePayment.await();
                        return "paid";
                    }
            ));
            assertThat(paymentStarted.await(1, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> payment.execute(
                    RequestDeadline.after(Duration.ofSeconds(1), Clock.systemUTC()),
                    noRetry,
                    context -> "second-payment"
            )).isInstanceOf(BulkheadFullException.class);

            String catalogResult = catalog.execute(
                    RequestDeadline.after(Duration.ofSeconds(1), Clock.systemUTC()),
                    noRetry,
                    context -> "product-42"
            );
            assertThat(catalogResult).isEqualTo("product-42");

            releasePayment.countDown();
            assertThat(activePayment.get()).isEqualTo("paid");
        } finally {
            releasePayment.countDown();
            callers.shutdownNow();
            dependencyThreads.shutdownNow();
        }
    }

    @Test
    void downstreamShouldReceiveOnlyItsAllocatedPartOfTheDeadline() throws Exception {
        Clock clock = Clock.systemUTC();
        RequestDeadline incoming = RequestDeadline.after(Duration.ofSeconds(2), clock);
        ExecutorService dependencyThreads = Executors.newSingleThreadExecutor();

        try (TimeoutExecutor timeoutExecutor = new TimeoutExecutor(dependencyThreads)) {
            OverloadProtectedRemoteClient client = client(
                    "catalog", 1, timeoutExecutor, new RetryBudget(0));

            String value = client.execute(
                    incoming,
                    new RetryPolicy(1, Duration.ofMillis(300), Duration.ofMillis(200)),
                    context -> {
                        RequestDeadline propagated = RequestDeadline.fromHeader(
                                context.deadlineHeader(), clock);
                        assertThat(propagated.expiresAt()).isBeforeOrEqualTo(incoming.expiresAt());
                        assertThat(context.allocatedTime()).isLessThanOrEqualTo(Duration.ofMillis(300));
                        return "ok";
                    }
            );

            assertThat(value).isEqualTo("ok");
        } finally {
            dependencyThreads.shutdownNow();
        }
    }

    @Test
    void expiredRequestShouldNotSpendARetryTokenOrStartAnotherAttempt() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-01T10:00:00Z"));
        RetryBudget retryBudget = new RetryBudget(1);
        AtomicInteger attempts = new AtomicInteger();
        ExecutorService dependencyThreads = Executors.newSingleThreadExecutor();

        try (TimeoutExecutor timeoutExecutor = new TimeoutExecutor(dependencyThreads)) {
            OverloadProtectedRemoteClient client = client(
                    "catalog", 1, timeoutExecutor, retryBudget);

            assertThatThrownBy(() -> client.execute(
                    RequestDeadline.after(Duration.ofMillis(500), clock),
                    new RetryPolicy(3, Duration.ofMillis(400), Duration.ofMillis(100)),
                    context -> {
                        attempts.incrementAndGet();
                        clock.advance(Duration.ofMillis(600));
                        throw new TransientDependencyException();
                    }
            )).isInstanceOf(DeadlineExceededException.class);

            assertThat(attempts).hasValue(1);
            assertThat(retryBudget.snapshot().remaining()).isEqualTo(1);
        } finally {
            dependencyThreads.shutdownNow();
        }
    }

    private static StormResult runFailureStorm(
            int logicalRequests,
            int maximumAttempts,
            int retryTokens
    ) throws Exception {
        AtomicInteger physicalAttempts = new AtomicInteger();
        RetryBudget retryBudget = new RetryBudget(retryTokens);
        ExecutorService dependencyThreads = Executors.newFixedThreadPool(2);

        try (TimeoutExecutor timeoutExecutor = new TimeoutExecutor(dependencyThreads)) {
            OverloadProtectedRemoteClient client = client(
                    "unstable-dependency", 10_000, timeoutExecutor, retryBudget);
            RetryPolicy retryPolicy = new RetryPolicy(
                    maximumAttempts, Duration.ofMillis(200), Duration.ofMillis(20));

            for (int request = 0; request < logicalRequests; request++) {
                assertThatThrownBy(() -> client.execute(
                        RequestDeadline.after(Duration.ofSeconds(2), Clock.systemUTC()),
                        retryPolicy,
                        context -> {
                            physicalAttempts.incrementAndGet();
                            throw new TransientDependencyException();
                        }
                )).isInstanceOf(TransientDependencyException.class);
            }
            return new StormResult(physicalAttempts.get(), retryBudget.snapshot());
        } finally {
            dependencyThreads.shutdownNow();
        }
    }

    private static OverloadProtectedRemoteClient client(
            String dependency,
            int bulkheadCapacity,
            TimeoutExecutor timeoutExecutor,
            RetryBudget retryBudget
    ) {
        CircuitBreaker breaker = new CircuitBreaker(
                dependency,
                10_000,
                Duration.ofSeconds(30),
                Clock.systemUTC(),
                failure -> failure instanceof TransientDependencyException
        );
        return new OverloadProtectedRemoteClient(
                breaker,
                new SemaphoreBulkhead(dependency, bulkheadCapacity),
                retryBudget,
                failure -> failure instanceof TransientDependencyException,
                timeoutExecutor
        );
    }

    private record StormResult(int physicalAttempts, RetryBudget.Snapshot budget) {
    }

    private static final class TransientDependencyException extends RuntimeException {
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) {
                throw new UnsupportedOperationException("laboratory clock uses UTC");
            }
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
