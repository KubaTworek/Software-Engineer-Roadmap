package pl.jakubtworek.backend_systems_lab_stage_1.block_a.threads.completable_future;

import java.util.concurrent.*;

public class UserAggregationService {

    private final ExecutorService executor =
            Executors.newFixedThreadPool(3);

    /**
     * ================================
     * FAN-OUT / FAN-IN using allOf
     * ================================
     *
     * FAN-OUT:
     *  - Start 3 independent async operations in parallel.
     *
     * FAN-IN:
     *  - Wait until all complete.
     *  - Aggregate their results.
     *
     * allOf() returns CompletableFuture<Void>.
     * It completes when all provided futures complete.
     *
     * Important:
     * - If any future fails, allOf completes exceptionally.
     * - We call join() only after allOf completes.
     */
    public AggregatedResponse fetchAll(int userId) {

        CompletableFuture<User> userF =
                CompletableFuture.supplyAsync(
                        () -> ServiceFetcher.fetchUser(userId), executor);

        CompletableFuture<Orders> ordersF =
                CompletableFuture.supplyAsync(
                        () -> ServiceFetcher.fetchOrders(userId), executor);

        CompletableFuture<Payments> paymentsF =
                CompletableFuture.supplyAsync(
                        () -> ServiceFetcher.fetchPayments(userId), executor);

        return CompletableFuture.allOf(userF, ordersF, paymentsF)
                .thenApply(v ->
                        new AggregatedResponse(
                                userF.join(),
                                ordersF.join(),
                                paymentsF.join()
                        )
                )
                .join();
    }


    /**
     * =================================
     * Alternative: thenCombine chaining
     * =================================
     *
     * thenCombine is suitable when combining 2 futures at a time.
     *
     * It avoids the Void-returning nature of allOf.
     *
     * Trade-off:
     * - Harder to scale dynamically for N futures.
     */
    public AggregatedResponse fetchWithThenCombine(int userId) {

        CompletableFuture<User> userF =
                CompletableFuture.supplyAsync(
                        () -> ServiceFetcher.fetchUser(userId), executor);

        CompletableFuture<Orders> ordersF =
                CompletableFuture.supplyAsync(
                        () -> ServiceFetcher.fetchOrders(userId), executor);

        CompletableFuture<Payments> paymentsF =
                CompletableFuture.supplyAsync(
                        () -> ServiceFetcher.fetchPayments(userId), executor);

        return userF
                .thenCombine(ordersF,
                        (user, orders) -> new Object[]{user, orders})
                .thenCombine(paymentsF,
                        (partial, payments) ->
                                new AggregatedResponse(
                                        (User) partial[0],
                                        (Orders) partial[1],
                                        payments))
                .join();
    }

    /**
     * =================================
     * Timeout + fallback example
     * =================================
     *
     * completeOnTimeout:
     * - If task exceeds timeout
     * - Completes normally with fallback value
     *
     * Different from orTimeout():
     * - orTimeout completes exceptionally with TimeoutException
     *
     * exceptionally():
     * - Handles any exception in pipeline
     * - Returns fallback value
     *
     * This demonstrates graceful degradation.
     */
    public String fetchWithTimeoutFallback() {

        return CompletableFuture
                .supplyAsync(ServiceFetcher::fetchSlowService, executor)
                .completeOnTimeout("fallback", 1, TimeUnit.SECONDS)
                .exceptionally(ex -> "fallback-error")
                .join();
    }

    /**
     * =================================
     * Exception handling demonstration
     * =================================
     *
     * If supplyAsync throws:
     * - CompletableFuture completes exceptionally
     *
     * exceptionally() transforms error into normal value.
     *
     * Without exceptionally():
     * - join() would throw CompletionException.
     */
    public String fetchWithErrorHandling() {

        return CompletableFuture
                .supplyAsync(ServiceFetcher::fetchFailingService, executor)
                .exceptionally(ex -> "recovered")
                .join();
    }

    public void shutdown() {
        executor.shutdown();
    }
}