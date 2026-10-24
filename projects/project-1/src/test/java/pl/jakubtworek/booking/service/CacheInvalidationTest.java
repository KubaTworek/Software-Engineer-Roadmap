package pl.jakubtworek.booking.service;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class CacheInvalidationTest {
    @Test void failedEvictionDoesNotMaskCommitOrPreventOtherCacheEvictions() {
        var second = new AtomicInteger();
        TransactionSynchronizationManager.initSynchronization();
        try {
            CacheInvalidation.afterCommit(() -> { throw new IllegalStateException("Redis unavailable"); }, second::incrementAndGet);
            assertEquals(0, second.get());
            TransactionSynchronizationManager.getSynchronizations().forEach(sync -> assertDoesNotThrow(sync::afterCommit));
            assertEquals(1, second.get());
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }
}
