package pl.jakubtworek.booking.service;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** DB jest źródłem prawdy. Błąd cache po COMMIT nie może zmienić sukcesu komendy w błąd HTTP. */
final class CacheInvalidation {
    private CacheInvalidation() { }
    static void afterCommit(Runnable... evictions) {
        Runnable attempt = () -> {
            for (Runnable eviction : evictions) {
                try { eviction.run(); }
                catch (RuntimeException unavailable) {
                    System.getLogger(CacheInvalidation.class.getName()).log(System.Logger.Level.WARNING,
                            "Cache eviction failed after commit; stale entry remains bounded by its TTL", unavailable);
                }
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) { attempt.run(); return; }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            public void afterCommit() { attempt.run(); }
        });
    }
}
