package pl.jakubtworek.backend_engineering.stage_1.block_a.reactive_streams;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/** Reactor adapter showing bounded concurrency while retaining the Reactive Streams contract. */
public final class ReactiveWorkPipeline {

    public Flux<String> execute(List<String> ids, int maximumConcurrency, Duration latency) {
        if (maximumConcurrency < 1) {
            throw new IllegalArgumentException("maximumConcurrency must be positive");
        }
        return Flux.fromIterable(ids)
                .flatMap(id -> Mono.delay(latency).map(ignored -> "loaded:" + id), maximumConcurrency);
    }

    public Mono<String> currentRequestId() {
        return Mono.deferContextual(context -> Mono.just(context.getOrDefault("requestId", "missing")));
    }
}
