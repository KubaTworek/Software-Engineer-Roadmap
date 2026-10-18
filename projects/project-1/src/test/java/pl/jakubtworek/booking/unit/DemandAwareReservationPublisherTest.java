package pl.jakubtworek.booking.unit;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.booking.education.reactive.DemandAwareReservationPublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;

import static org.assertj.core.api.Assertions.assertThat;

class DemandAwareReservationPublisherTest {

    @Test
    void emitsOnlyRequestedReservationsAndStopsAfterCancellation() {
        RecordingSubscriber subscriber = new RecordingSubscriber();
        new DemandAwareReservationPublisher<>(List.of("r-1", "r-2", "r-3"))
                .subscribe(subscriber);

        subscriber.subscription.request(1);
        assertThat(subscriber.items).containsExactly("r-1");

        subscriber.subscription.cancel();
        subscriber.subscription.request(10);
        assertThat(subscriber.items).containsExactly("r-1");
        assertThat(subscriber.completed).isFalse();
    }

    private static final class RecordingSubscriber implements Flow.Subscriber<String> {
        private final List<String> items = new ArrayList<>();
        private Flow.Subscription subscription;
        private boolean completed;

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
        }

        @Override
        public void onNext(String item) {
            items.add(item);
        }

        @Override
        public void onError(Throwable throwable) {
            throw new AssertionError("Unexpected publisher error", throwable);
        }

        @Override
        public void onComplete() {
            completed = true;
        }
    }
}
