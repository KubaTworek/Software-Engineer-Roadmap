package pl.jakubtworek.backend.order.application;

import io.micrometer.core.instrument.*;
import org.springframework.stereotype.Component;
import pl.jakubtworek.backend.order.api.OrderResponse;
import pl.jakubtworek.backend.order.domain.OrderStatus;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.*;

/** SLI użytkownika: poprawne żądanie kończy się PAID w <=3 s. Worker nie poprawia historycznego SLI. */
@Component
public final class CheckoutMetrics {
    private final Counter good,bad;
    private final Timer latency;
    private final LongSupplier nanos;
    @org.springframework.beans.factory.annotation.Autowired
    public CheckoutMetrics(MeterRegistry registry){this(registry,System::nanoTime);}
    CheckoutMetrics(MeterRegistry registry,LongSupplier nanos) {
        this.nanos=nanos;good=registry.counter("app_checkout_requests","outcome","good");bad=registry.counter("app_checkout_requests","outcome","bad");
        latency=Timer.builder("app_checkout_duration").publishPercentileHistogram().serviceLevelObjectives(Duration.ofSeconds(3)).register(registry);
    }
    public OrderResponse observe(Supplier<OrderResponse> action) {
        long start=nanos.getAsLong();OrderResponse result;
        try{result=action.get();}
        catch(IllegalArgumentException invalid){throw invalid;} // Błędny zasób/klucz nie należy do populacji poprawnych żądań.
        catch(RuntimeException failure){record(start,false);throw failure;}
        record(start,result.status()==OrderStatus.PAID);return result;
    }
    private void record(long start,boolean paid){long elapsed=Math.max(0,nanos.getAsLong()-start);latency.record(elapsed,TimeUnit.NANOSECONDS);(paid&&elapsed<=3_000_000_000L?good:bad).increment();}
}
