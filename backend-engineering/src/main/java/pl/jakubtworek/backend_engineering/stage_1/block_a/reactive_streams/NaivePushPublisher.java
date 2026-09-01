package pl.jakubtworek.backend_engineering.stage_1.block_a.reactive_streams;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Flow;

/** Deliberate counterexample: the first request causes every element to be pushed. */
public final class NaivePushPublisher<T> implements Flow.Publisher<T> {

    private final List<T> elements;

    public NaivePushPublisher(List<T> elements) {
        this.elements = List.copyOf(elements);
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        Objects.requireNonNull(subscriber, "subscriber");
        subscriber.onSubscribe(new Flow.Subscription() {
            private boolean terminated;

            @Override
            public void request(long ignoredDemand) {
                if (terminated) {
                    return;
                }
                terminated = true;
                elements.forEach(subscriber::onNext);
                subscriber.onComplete();
            }

            @Override
            public void cancel() {
                terminated = true;
            }
        });
    }
}
