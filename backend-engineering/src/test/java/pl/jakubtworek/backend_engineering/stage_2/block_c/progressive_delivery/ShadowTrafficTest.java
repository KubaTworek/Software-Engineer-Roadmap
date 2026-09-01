package pl.jakubtworek.backend_engineering.stage_2.block_c.progressive_delivery;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;

class ShadowTrafficTest {

    @Test
    void shadowReceivesSanitizedInputAndCannotChangePrimaryResponse() {
        ShadowTrafficRouter router = new ShadowTrafficRouter(Runnable::run);
        AtomicInteger productionWrites = new AtomicInteger();
        AtomicReference<ShadowTrafficRouter.ShadowRequest> mirrored = new AtomicReference<>();

        ShadowTrafficRouter.Response response = router.route(
                new ShadowTrafficRouter.ProductionRequest("trace-1", "order-42", "Bearer secret"),
                request -> {
                    productionWrites.incrementAndGet();
                    return new ShadowTrafficRouter.Response(201, "created");
                },
                mirrored::set,
                ignored -> {});

        assertThat(response).isEqualTo(new ShadowTrafficRouter.Response(201, "created"));
        assertThat(productionWrites).hasValue(1);
        assertThat(mirrored.get()).isEqualTo(new ShadowTrafficRouter.ShadowRequest("trace-1", "order-42"));
    }

    @Test
    void shadowFailureIsObservedButNeverFailsTheUserRequest() {
        ShadowTrafficRouter router = new ShadowTrafficRouter(Runnable::run);
        List<String> failures = new ArrayList<>();

        ShadowTrafficRouter.Response response = router.route(
                new ShadowTrafficRouter.ProductionRequest("trace-2", "payload", null),
                request -> new ShadowTrafficRouter.Response(200, "primary"),
                request -> { throw new IllegalStateException("candidate failed"); },
                failure -> failures.add(failure.getMessage()));

        assertThat(response.body()).isEqualTo("primary");
        assertThat(failures).containsExactly("candidate failed");
    }

    @Test
    void primaryResponseDoesNotWaitForShadowExecution() {
        List<Runnable> shadowQueue = new ArrayList<>();
        ShadowTrafficRouter router = new ShadowTrafficRouter(shadowQueue::add);
        AtomicInteger shadowCalls = new AtomicInteger();

        ShadowTrafficRouter.Response response = router.route(
                new ShadowTrafficRouter.ProductionRequest("trace-3", "payload", null),
                request -> new ShadowTrafficRouter.Response(200, "primary"),
                request -> shadowCalls.incrementAndGet(),
                ignored -> {});

        assertThat(response.body()).isEqualTo("primary");
        assertThat(shadowCalls).hasValue(0);
        shadowQueue.getFirst().run();
        assertThat(shadowCalls).hasValue(1);
    }

    @Test
    void saturatedShadowExecutorDoesNotRejectPrimaryTraffic() {
        List<String> failures = new ArrayList<>();
        ShadowTrafficRouter router = new ShadowTrafficRouter(command -> {
            throw new RejectedExecutionException("shadow queue full");
        });

        ShadowTrafficRouter.Response response = router.route(
                new ShadowTrafficRouter.ProductionRequest("trace-4", "payload", null),
                request -> new ShadowTrafficRouter.Response(200, "primary"),
                request -> {},
                failure -> failures.add(failure.getMessage()));

        assertThat(response.body()).isEqualTo("primary");
        assertThat(failures).containsExactly("shadow queue full");
    }
}
