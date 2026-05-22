package pl.jakubtworek.backend_engineering.stage_2.block_b.testing.outbox;

import pl.jakubtworek.backend_engineering.stage_2.block_b.testing.events.TestDomainEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * In-memory outbox table used by integration tests.
 *
 * Production systems should store this data in the same database transaction
 * as the business state change.
 */
public class InMemoryOutbox {

    private final List<OutboxEntry> entries = new ArrayList<>();

    /**
     * Adds an event to the outbox as unpublished.
     */
    public void add(TestDomainEvent event) {
        entries.add(new OutboxEntry(
                UUID.randomUUID(),
                event,
                false
        ));
    }

    /**
     * Returns unpublished outbox entries.
     */
    public List<OutboxEntry> unpublishedEntries() {
        return entries.stream()
                .filter(entry -> !entry.published())
                .toList();
    }

    /**
     * Marks an entry as successfully published.
     */
    public void markPublished(UUID outboxId) {
        for (int i = 0; i < entries.size(); i++) {
            OutboxEntry entry = entries.get(i);

            if (entry.outboxId().equals(outboxId)) {
                entries.set(i, entry.markPublished());
                return;
            }
        }
    }

    /**
     * Returns true when all outbox entries were published.
     */
    public boolean allPublished() {
        return entries.stream().allMatch(OutboxEntry::published);
    }
}