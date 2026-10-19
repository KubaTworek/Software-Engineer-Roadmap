package pl.jakubtworek.backend_engineering.stage_1.block_a.reactive_streams;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

class ReactorRuntimeTest {

    @Test
    void stepVerifierControlsDemandAgainstARealReactorPublisher() {
        Flux<Integer> publisher = Flux.range(1, 5);

        StepVerifier.create(publisher, 0)
                .thenRequest(2)
                .expectNext(1, 2)
                .thenRequest(3)
                .expectNext(3, 4, 5)
                .verifyComplete();
    }

    @Test
    void cancellationReachesTheRuntimePipeline() {
        AtomicBoolean cancelled = new AtomicBoolean();

        StepVerifier.withVirtualTime(() -> Flux.interval(Duration.ofSeconds(1)).doOnCancel(() -> cancelled.set(true)))
                .expectSubscription()
                .thenAwait(Duration.ofSeconds(2))
                .expectNext(0L, 1L)
                .thenCancel()
                .verify();

        org.assertj.core.api.Assertions.assertThat(cancelled).isTrue();
    }

    @Test
    void reactorContextCarriesRequestMetadataWithoutThreadLocal() {
        ReactiveWorkPipeline pipeline = new ReactiveWorkPipeline();

        StepVerifier.create(pipeline.currentRequestId().contextWrite(context -> context.put("requestId", "req-42")))
                .expectNext("req-42")
                .verifyComplete();
    }

    @Test
    void boundedConcurrencyCompletesTheSameLogicalWorkload() {
        ReactiveWorkPipeline pipeline = new ReactiveWorkPipeline();

        StepVerifier.withVirtualTime(() -> pipeline.execute(List.of("a", "b", "c"), 2, Duration.ofSeconds(1)))
                .thenAwait(Duration.ofSeconds(2))
                .expectNextCount(3)
                .verifyComplete();
    }
}
