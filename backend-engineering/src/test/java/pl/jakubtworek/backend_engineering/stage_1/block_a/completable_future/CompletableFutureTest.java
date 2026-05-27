package pl.jakubtworek.backend_engineering.stage_1.block_a.completable_future;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.backend_engineering.stage_1.block_a.completable_future.AggregatedResponse;
import pl.jakubtworek.backend_engineering.stage_1.block_a.completable_future.UserAggregationService;

import static org.junit.jupiter.api.Assertions.*;

class CompletableFutureTest {

    @Test
    void shouldAggregateAllServices() {
        UserAggregationService service = new UserAggregationService();

        AggregatedResponse response1 = service.fetchAll(1);

        assertNotNull(response1.user());
        assertNotNull(response1.orders());
        assertNotNull(response1.payments());

        AggregatedResponse response2 = service.fetchWithThenCombine(1);

        assertNotNull(response2.user());
        assertNotNull(response2.orders());
        assertNotNull(response2.payments());

        service.shutdown();
    }

    @Test
    void shouldReturnFallbackOnTimeout() {
        UserAggregationService service = new UserAggregationService();

        String result = service.fetchWithTimeoutFallback();

        assertEquals("fallback", result);

        service.shutdown();
    }

    @Test
    void shouldRecoverFromException() {
        UserAggregationService service = new UserAggregationService();

        String result = service.fetchWithErrorHandling();

        assertEquals("recovered", result);

        service.shutdown();
    }
}