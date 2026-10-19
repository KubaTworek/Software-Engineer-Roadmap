package pl.jakubtworek.backend_engineering.stage_1.block_a.completable_future;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CompletableFutureTest {

    @Test
    void shouldAggregateIndependentCallsWithAllOf() {
        try (UserAggregationService service = newService(Duration.ofMillis(20))) {
            AggregatedResponse response = service.fetchAllAsync(7).join();

            assertEquals(new User(7, "User-7"), response.user());
            assertEquals(new Orders(7, 3), response.orders());
            assertEquals(new Payments(7, true), response.payments());
        }
    }

    @Test
    void shouldAggregateIndependentCallsWithThenCombine() {
        try (UserAggregationService service = newService(Duration.ofMillis(20))) {
            AggregatedResponse response = service.fetchWithThenCombineAsync(7).join();

            assertEquals(7, response.user().id());
            assertEquals(7, response.orders().userId());
            assertEquals(7, response.payments().userId());
        }
    }

    @Test
    void shouldComposeDependentCallsWithoutNestedFuture() {
        try (UserAggregationService service = newService(Duration.ofMillis(20))) {
            Orders orders = service.fetchOrdersForExistingUserAsync(9).join();

            assertEquals(new Orders(9, 3), orders);
        }
    }

    @Test
    void shouldReturnFallbackOnTimeout() {
        try (UserAggregationService service = newService(Duration.ofMillis(20))) {
            assertEquals("fallback", service.fetchWithTimeoutFallbackAsync().join());
        }
    }

    @Test
    void shouldRecoverFromFailure() {
        try (UserAggregationService service = newService(Duration.ofMillis(20))) {
            assertEquals("recovered", service.fetchWithErrorHandlingAsync().join());
        }
    }

    @Test
    void shouldRejectNonPositiveTimeout() {
        assertThrows(IllegalArgumentException.class, () ->
                new UserAggregationService(
                        Executors.newSingleThreadExecutor(),
                        new ImmediateServiceFetcher(),
                        Duration.ZERO
                ));
    }

    private static UserAggregationService newService(Duration timeout) {
        return new UserAggregationService(
                Executors.newFixedThreadPool(3),
                new ImmediateServiceFetcher(),
                timeout
        );
    }

    /** Fast deterministic replacement for the latency-oriented demo fetcher. */
    private static final class ImmediateServiceFetcher extends ServiceFetcher {
        @Override
        public User fetchUser(int id) {
            return new User(id, "User-" + id);
        }

        @Override
        public Orders fetchOrders(int id) {
            return new Orders(id, 3);
        }

        @Override
        public Payments fetchPayments(int id) {
            return new Payments(id, true);
        }

        @Override
        public String fetchSlowService() {
            try {
                Thread.sleep(250);
                return "slow-data";
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return "interrupted";
            }
        }

        @Override
        public String fetchFailingService() {
            throw new IllegalStateException("Downstream failure");
        }
    }
}
