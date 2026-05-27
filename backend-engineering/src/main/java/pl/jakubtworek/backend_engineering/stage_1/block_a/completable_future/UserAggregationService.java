package pl.jakubtworek.backend_engineering.stage_1.block_a.completable_future;

import java.util.concurrent.*;

public class UserAggregationService {

    // Fixed thread pool used to execute async service calls
    private final ExecutorService executor =
            Executors.newFixedThreadPool(3);

    /**
     * Runs three independent service calls in parallel and aggregates results.
     *
     * Key points in this example:
     * - each supplyAsync() schedules work on the executor
     * - allOf() waits until all futures complete
     * - join() retrieves results after completion
     */
    public AggregatedResponse fetchAll(int userId) {

        // async call fetching user data
        CompletableFuture<User> userF =
                CompletableFuture.supplyAsync(
                        () -> ServiceFetcher.fetchUser(userId), executor);

        // async call fetching orders
        CompletableFuture<Orders> ordersF =
                CompletableFuture.supplyAsync(
                        () -> ServiceFetcher.fetchOrders(userId), executor);

        // async call fetching payments
        CompletableFuture<Payments> paymentsF =
                CompletableFuture.supplyAsync(
                        () -> ServiceFetcher.fetchPayments(userId), executor);

        // wait for all futures to complete and then aggregate their results
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
     * Combines results step-by-step using thenCombine.
     *
     * In this example:
     * - first combines user and orders
     * - then combines that intermediate result with payments
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
                // combine user + orders
                .thenCombine(ordersF,
                        (user, orders) -> new Object[]{user, orders})

                // combine previous result with payments
                .thenCombine(paymentsF,
                        (partial, payments) ->
                                new AggregatedResponse(
                                        (User) partial[0],
                                        (Orders) partial[1],
                                        payments))
                .join();
    }

    /**
     * Demonstrates timeout handling with fallback value.
     *
     * In this example:
     * - fetchSlowService() may take longer than expected
     * - completeOnTimeout provides default value if timeout occurs
     * - exceptionally handles unexpected failures
     */
    public String fetchWithTimeoutFallback() {

        return CompletableFuture
                .supplyAsync(ServiceFetcher::fetchSlowService, executor)

                // if execution takes longer than 1 second, return fallback
                .completeOnTimeout("fallback", 1, TimeUnit.SECONDS)

                // handle any other exception
                .exceptionally(ex -> "fallback-error")

                .join();
    }

    /**
     * Demonstrates simple exception recovery.
     *
     * In this example:
     * - fetchFailingService() throws an exception
     * - exceptionally converts failure into a normal result
     */
    public String fetchWithErrorHandling() {

        return CompletableFuture
                .supplyAsync(ServiceFetcher::fetchFailingService, executor)

                // convert exception into fallback value
                .exceptionally(ex -> "recovered")

                .join();
    }

    // shutdown executor after finishing async operations
    public void shutdown() {
        executor.shutdown();
    }
}