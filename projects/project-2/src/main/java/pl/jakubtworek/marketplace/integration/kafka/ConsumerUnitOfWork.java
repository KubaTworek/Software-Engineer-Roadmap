package pl.jakubtworek.marketplace.integration.kafka;

import java.util.function.Supplier;

/**
 * Granica lokalnej transakcji konsumenta.
 *
 * At-least-once nie wymaga wspólnej transakcji PostgreSQL–Kafka. Wymaga jednak,
 * aby efekt biznesowy, nowe rekordy outboxa i marker idempotencji zostały
 * zatwierdzone razem w bazie należącej do konsumenta. Offset jest commitowany
 * dopiero po zakończeniu tej jednostki pracy.
 */
@FunctionalInterface
public interface ConsumerUnitOfWork {

    <T> T execute(Supplier<T> work);

    /**
     * Wariant dla szybkich laboratoriów in-memory. Nie udaje transakcji — dzięki
     * temu test musi jawnie wybrać prawdziwy adapter, gdy dowodzi rollbacku DB.
     */
    static ConsumerUnitOfWork direct() {
        return new ConsumerUnitOfWork() {
            @Override
            public <T> T execute(Supplier<T> work) {
                return work.get();
            }
        };
    }
}
