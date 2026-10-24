package pl.jakubtworek.backend.order.application;
import org.junit.jupiter.api.Test;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import pl.jakubtworek.backend.order.api.OrderResponse;
import pl.jakubtworek.backend.order.domain.OrderStatus;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
class CheckoutMetricsTest {
    @Test void lateSuccessAndPendingAreBadAndInvalidBusinessRequestsAreExcluded() {
        var registry=new SimpleMeterRegistry();var nanos=new AtomicLong();var metrics=new CheckoutMetrics(registry,nanos::get);
        metrics.observe(()->{nanos.addAndGet(3_100_000_000L);return response(OrderStatus.PAID);});
        metrics.observe(()->response(OrderStatus.PAYMENT_UNKNOWN));
        metrics.observe(()->response(OrderStatus.PAID));
        assertThrows(IllegalArgumentException.class,()->metrics.observe(()->{throw new IllegalArgumentException("bad id");}));
        assertThrows(IllegalStateException.class,()->metrics.observe(()->{throw new IllegalStateException("database down");}));
        assertEquals(1,registry.get("app_checkout_requests").tag("outcome","good").counter().count());
        assertEquals(3,registry.get("app_checkout_requests").tag("outcome","bad").counter().count());
        assertEquals(4,registry.get("app_checkout_duration").timer().count());
    }
    private OrderResponse response(OrderStatus status){return new OrderResponse(UUID.randomUUID(),UUID.randomUUID(),"owner",BigDecimal.ONE,status,Instant.now(),null);}
}
