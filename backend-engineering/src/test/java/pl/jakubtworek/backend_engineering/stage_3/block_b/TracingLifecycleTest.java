package pl.jakubtworek.backend_engineering.stage_3.block_b;

import io.opentelemetry.api.trace.Span;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import pl.jakubtworek.backend_engineering.stage_3.block_b.tracing.CheckoutSpanFactory;
import pl.jakubtworek.backend_engineering.stage_3.block_b.tracing.OrderCacheCodec;
import pl.jakubtworek.backend_engineering.stage_3.block_b.tracing.SpanScope;
import pl.jakubtworek.backend_engineering.stage_3.block_b.tracing.TracedOrderRepository;
import pl.jakubtworek.backend_engineering.stage_3.block_b.tracing.TracedRedisClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TracingLifecycleTest {

    @Test
    void recordsRedisFailureOnDependencySpanBeforeClosingIt() {
        CheckoutSpanFactory spanFactory = mock(CheckoutSpanFactory.class);
        SpanScope spanScope = mock(SpanScope.class);
        Span span = mock(Span.class);
        RuntimeException failure = new RuntimeException("redis unavailable");
        when(spanFactory.startRedisGetSpan("0")).thenReturn(spanScope);
        when(spanScope.span()).thenReturn(span);

        TracedRedisClient client = new TracedRedisClient(spanFactory, key -> {
            throw failure;
        });

        assertThatThrownBy(() -> client.getOrderFromCache("order:42")).isSameAs(failure);

        InOrder lifecycle = inOrder(span, spanScope);
        lifecycle.verify(span).recordException(failure);
        lifecycle.verify(spanScope).close();
    }

    @Test
    void recordsRepositoryFailureOnDatabaseSpan() {
        CheckoutSpanFactory spanFactory = mock(CheckoutSpanFactory.class);
        SpanScope spanScope = mock(SpanScope.class);
        Span span = mock(Span.class);
        RuntimeException failure = new RuntimeException("database unavailable");
        when(spanFactory.startPostgresSelectOrdersSpan()).thenReturn(spanScope);
        when(spanScope.span()).thenReturn(span);

        TracedOrderRepository repository = new TracedOrderRepository(spanFactory, orderId -> {
            throw failure;
        });

        assertThatThrownBy(() -> repository.findOrder("42")).isSameAs(failure);

        verify(span).recordException(failure);
    }

    @Test
    void validatesRecordsAtTheTracingBoundary() {
        assertThatIllegalRecordIsRejected();
    }

    @Test
    void cacheCodecRoundTripsVersionedOrderAndRejectsUnknownSchema() {
        OrderCacheCodec codec = new OrderCacheCodec();
        TracedOrderRepository.OrderRecord order =
                new TracedOrderRepository.OrderRecord("order:42/PL", 2599, "PLN");

        assertThat(codec.decode(codec.encode(order))).isEqualTo(order);
        assertThatThrownBy(() -> codec.decode("v2:unknown:1000:currency"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("schema");
    }

    private static void assertThatIllegalRecordIsRejected() {
        assertThatThrownBy(() -> new TracedOrderRepository.OrderRecord("42", -1, "PLN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("totalCents");
    }
}
