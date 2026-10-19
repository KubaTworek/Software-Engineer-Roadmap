package pl.jakubtworek.backend_engineering.stage_1.block_a.reactive_streams;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;

import static org.assertj.core.api.Assertions.assertThat;

class ReactiveStreamsDemandTest {

    @Test
    void correctPublisherNeverEmitsBeyondDemand() {
        RecordingSubscriber<Integer> subscriber = new RecordingSubscriber<>();
        new DemandAwarePublisher<>(List.of(1, 2, 3, 4, 5)).subscribe(subscriber);

        subscriber.request(2);
        assertThat(subscriber.items).containsExactly(1, 2);
        assertThat(subscriber.completed).isFalse();

        subscriber.request(2);
        assertThat(subscriber.items).containsExactly(1, 2, 3, 4);

        subscriber.request(1);
        assertThat(subscriber.items).containsExactly(1, 2, 3, 4, 5);
        assertThat(subscriber.completed).isTrue();
    }

    @Test
    void cancellationStopsFurtherSignals() {
        RecordingSubscriber<Integer> subscriber = new RecordingSubscriber<>();
        new DemandAwarePublisher<>(List.of(1, 2, 3)).subscribe(subscriber);

        subscriber.request(1);
        subscriber.cancel();
        subscriber.request(10);

        assertThat(subscriber.items).containsExactly(1);
        assertThat(subscriber.completed).isFalse();
    }

    @Test
    void nonPositiveDemandTerminatesWithAnError() {
        RecordingSubscriber<Integer> subscriber = new RecordingSubscriber<>();
        new DemandAwarePublisher<>(List.of(1)).subscribe(subscriber);

        subscriber.request(0);

        assertThat(subscriber.error)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("demand must be positive");
        assertThat(subscriber.items).isEmpty();
    }

    @Test
    void naivePublisherDemonstratesWhyIgnoringDemandIsUnsafe() {
        RecordingSubscriber<Integer> subscriber = new RecordingSubscriber<>();
        new NaivePushPublisher<>(List.of(1, 2, 3, 4, 5)).subscribe(subscriber);

        subscriber.request(1);

        assertThat(subscriber.items).hasSize(5);
    }

    private static final class RecordingSubscriber<T> implements Flow.Subscriber<T> {
        private final List<T> items = new ArrayList<>();
        private Flow.Subscription subscription;
        private Throwable error;
        private boolean completed;

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
        }

        @Override
        public void onNext(T item) {
            items.add(item);
        }

        @Override
        public void onError(Throwable throwable) {
            error = throwable;
        }

        @Override
        public void onComplete() {
            completed = true;
        }

        private void request(long demand) {
            subscription.request(demand);
        }

        private void cancel() {
            subscription.cancel();
        }
    }
}
