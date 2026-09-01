package pl.jakubtworek.backend_engineering.stage_1.block_a.reactive_streams;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Flow;

/** Minimal publisher that never emits more elements than its subscriber requested. */
public final class DemandAwarePublisher<T> implements Flow.Publisher<T> {

    private final List<T> elements;

    public DemandAwarePublisher(List<T> elements) {
        this.elements = List.copyOf(elements);
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        Objects.requireNonNull(subscriber, "subscriber");
        subscriber.onSubscribe(new DemandSubscription(subscriber, elements.iterator()));
    }

    private final class DemandSubscription implements Flow.Subscription {
        private final Flow.Subscriber<? super T> subscriber;
        private final Iterator<T> iterator;
        private boolean cancelled;
        private boolean completed;
        private long demand;

        private DemandSubscription(Flow.Subscriber<? super T> subscriber, Iterator<T> iterator) {
            this.subscriber = subscriber;
            this.iterator = iterator;
        }

        @Override
        public synchronized void request(long requested) {
            if (cancelled || completed) {
                return;
            }
            if (requested <= 0) {
                cancelled = true;
                subscriber.onError(new IllegalArgumentException("demand must be positive"));
                return;
            }
            demand = saturatingAdd(demand, requested);
            while (!cancelled && demand > 0 && iterator.hasNext()) {
                T next = iterator.next();
                demand--;
                subscriber.onNext(next);
            }
            if (!cancelled && !iterator.hasNext()) {
                completed = true;
                subscriber.onComplete();
            }
        }

        @Override
        public synchronized void cancel() {
            cancelled = true;
        }

        private long saturatingAdd(long current, long increment) {
            long sum = current + increment;
            return sum < 0 ? Long.MAX_VALUE : sum;
        }
    }
}
