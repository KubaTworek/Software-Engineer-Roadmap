package pl.jakubtworek.chatsystem.lifecycle;

import java.time.*;
import java.util.List;

/**
 * Deterministyczny model fizycznego kasowania tombstone. Usunięcie z UI nie jest
 * równoznaczne z natychmiastowym usunięciem z każdego nośnika i backupu.
 */
public final class MessageRetentionPolicy {
    public enum Decision { KEEP_TOMBSTONE, ERASE_PRIMARY_CONTENT, PHYSICALLY_DELETE }
    public record Context(long messageSequence, Instant deletedAt, List<Long> activeDeviceCursors,
                          boolean legalHold, Instant backupRetentionUntil) {}
    private final Duration tombstoneGrace;
    public MessageRetentionPolicy(Duration tombstoneGrace) { this.tombstoneGrace = tombstoneGrace; }

    public Decision decide(Context context, Instant now) {
        if (context.legalHold()) return Decision.KEEP_TOMBSTONE;
        if (now.isBefore(context.deletedAt().plus(tombstoneGrace))) return Decision.KEEP_TOMBSTONE;
        boolean everyDeviceObservedDelete = context.activeDeviceCursors().stream()
                .allMatch(cursor -> cursor >= context.messageSequence());
        if (!everyDeviceObservedDelete) return Decision.ERASE_PRIMARY_CONTENT;
        if (context.backupRetentionUntil() != null && now.isBefore(context.backupRetentionUntil())) {
            return Decision.ERASE_PRIMARY_CONTENT;
        }
        return Decision.PHYSICALLY_DELETE;
    }
}
