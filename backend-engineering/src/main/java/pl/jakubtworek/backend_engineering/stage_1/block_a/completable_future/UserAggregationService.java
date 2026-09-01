package pl.jakubtworek.backend_engineering.stage_1.block_a.completable_future;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Demonstrates composition of independent and dependent asynchronous calls.
 *
 * <p>The service deliberately returns {@link CompletableFuture} from its API.
 * Blocking with {@code join()} belongs at an explicit application boundary,
 * not in the middle of an asynchronous pipeline.</p>
 */
public final class UserAggregationService implements AutoCloseable {

    private final ExecutorService executor;
    private final ServiceFetcher fetcher;
    private final Duration timeout;

    public UserAggregationService() {
        this(Executors.newFixedThreadPool(3), new ServiceFetcher(), Duration.ofSeconds(1));
    }

    public UserAggregationService(
            ExecutorService executor,
            ServiceFetcher fetcher,
            Duration timeout
    ) {
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
        this.fetcher = Objects.requireNonNull(fetcher, "fetcher must not be null");
        this.timeout = requirePositive(timeout);
    }

    /** Runs three independent calls concurrently and aggregates them with allOf. */
    public CompletableFuture<AggregatedResponse> fetchAllAsync(int userId) {
        CompletableFuture<User> userFuture =
                CompletableFuture.supplyAsync(() -> fetcher.fetchUser(userId), executor);
        CompletableFuture<Orders> ordersFuture =
                CompletableFuture.supplyAsync(() -> fetcher.fetchOrders(userId), executor);
        CompletableFuture<Payments> paymentsFuture =
                CompletableFuture.supplyAsync(() -> fetcher.fetchPayments(userId), executor);

        return CompletableFuture.allOf(userFuture, ordersFuture, paymentsFuture)
                .thenApply(ignored -> new AggregatedResponse(
                        userFuture.join(),
                        ordersFuture.join(),
                        paymentsFuture.join()
                ));
    }

    /** Combines independent results without untyped intermediate arrays. */
    public CompletableFuture<AggregatedResponse> fetchWithThenCombineAsync(int userId) {
        CompletableFuture<User> userFuture =
                CompletableFuture.supplyAsync(() -> fetcher.fetchUser(userId), executor);
        CompletableFuture<Orders> ordersFuture =
                CompletableFuture.supplyAsync(() -> fetcher.fetchOrders(userId), executor);
        CompletableFuture<Payments> paymentsFuture =
                CompletableFuture.supplyAsync(() -> fetcher.fetchPayments(userId), executor);

        return userFuture
                .thenCombine(ordersFuture, UserWithOrders::new)
                .thenCombine(paymentsFuture, (partial, payments) ->
                        new AggregatedResponse(partial.user(), partial.orders(), payments));
    }

    /**
     * Demonstrates thenCompose for a dependent call: orders cannot be requested
     * until the user lookup has completed successfully.
     */
    public CompletableFuture<Orders> fetchOrdersForExistingUserAsync(int userId) {
        return CompletableFuture
                .supplyAsync(() -> fetcher.fetchUser(userId), executor)
                .thenCompose(user -> CompletableFuture.supplyAsync(
                        () -> fetcher.fetchOrders(user.id()), executor));
    }

    /** Converts a slow response into a normal fallback value. */
    public CompletableFuture<String> fetchWithTimeoutFallbackAsync() {
        return CompletableFuture
                .supplyAsync(fetcher::fetchSlowService, executor)
                .completeOnTimeout("fallback", timeout.toMillis(), TimeUnit.MILLISECONDS)
                .exceptionally(failure -> "fallback-error");
    }

    /** Converts a failed downstream call into an explicit recovery value. */
    public CompletableFuture<String> fetchWithErrorHandlingAsync() {
        return CompletableFuture
                .supplyAsync(fetcher::fetchFailingService, executor)
                .exceptionally(failure -> "recovered");
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static Duration requirePositive(Duration duration) {
        Objects.requireNonNull(duration, "timeout must not be null");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        return duration;
    }

    private record UserWithOrders(User user, Orders orders) {
    }
}
