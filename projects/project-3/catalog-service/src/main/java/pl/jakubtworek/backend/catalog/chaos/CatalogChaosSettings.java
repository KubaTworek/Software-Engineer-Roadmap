package pl.jakubtworek.backend.catalog.chaos;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class CatalogChaosSettings {
    private final AtomicLong databaseDelayMs = new AtomicLong(0);

    public long databaseDelayMs() {
        return databaseDelayMs.get();
    }

    public void setDatabaseDelayMs(long value) {
        databaseDelayMs.set(Math.max(0, value));
    }

    public void reset() {
        databaseDelayMs.set(0);
    }
}
