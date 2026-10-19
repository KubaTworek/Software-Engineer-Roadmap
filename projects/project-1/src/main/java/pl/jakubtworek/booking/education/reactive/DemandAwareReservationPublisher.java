package pl.jakubtworek.booking.education.reactive;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Flow;

/**
 * Minimalne laboratorium semantyki Reactive Streams bez zależności od frameworka.
 *
 * <p>Publisher nie może wysłać więcej elementów, niż subscriber zamówił przez
 * {@link Flow.Subscription#request(long)}. To właśnie demand jest backpressure;
 * samo przeniesienie pracy na inny scheduler nie chroni wolnego konsumenta.
 * Implementacja jest synchroniczna celowo, aby widoczny był kontrakt protokołu,
 * a nie API Reactor/RxJava.</p>
 */
public final class DemandAwareReservationPublisher<T> implements Flow.Publisher<T> {

    private final List<T> items;

    public DemandAwareReservationPublisher(List<T> items) {
        this.items = List.copyOf(items);
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        Objects.requireNonNull(subscriber, "subscriber");
        subscriber.onSubscribe(new DemandSubscription(subscriber));
    }

    private final class DemandSubscription implements Flow.Subscription {
        private final Flow.Subscriber<? super T> subscriber;
        private int index;
        private boolean cancelled;
        private boolean completed;

        private DemandSubscription(Flow.Subscriber<? super T> subscriber) {
            this.subscriber = subscriber;
        }

        @Override
        public synchronized void request(long demand) {
            if (cancelled || completed) {
                return;
            }
            if (demand <= 0) {
                cancelled = true;
                subscriber.onError(new IllegalArgumentException("demand must be positive"));
                return;
            }

            long remainingDemand = demand;
            while (!cancelled && remainingDemand > 0 && index < items.size()) {
                subscriber.onNext(items.get(index++));
                remainingDemand--;
            }
            if (!cancelled && index == items.size()) {
                completed = true;
                subscriber.onComplete();
            }
        }

        @Override
        public synchronized void cancel() {
            // Anulowanie jest sygnałem protokołu. Po nim publisher nie może
            // wywołać już onNext, onError ani onComplete dla tego subscribera.
            cancelled = true;
        }
    }
}
