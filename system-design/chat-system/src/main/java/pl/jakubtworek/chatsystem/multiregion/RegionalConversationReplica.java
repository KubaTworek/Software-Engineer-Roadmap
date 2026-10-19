package pl.jakubtworek.chatsystem.multiregion;

import java.util.UUID;

/** Model repliki pokazujący osobno bezpieczeństwo zapisu i read-your-writes. */
public final class RegionalConversationReplica {
    private final UUID conversationId;
    private final ConversationLeadership leadership;
    private long committedSequence;
    private long appliedSequence;
    public RegionalConversationReplica(UUID conversationId, ConversationLeadership leadership) {
        this.conversationId = conversationId; this.leadership = leadership;
    }
    public synchronized long append(ConversationLeadership.Lease lease) {
        leadership.assertCurrent(conversationId, lease);
        return ++committedSequence;
    }
    public synchronized void replicateThrough(long sequence) { appliedSequence = Math.max(appliedSequence, sequence); }
    public synchronized long read(long minimumObservedSequence) {
        if (appliedSequence < minimumObservedSequence) {
            throw new StaleReplicaException(appliedSequence, minimumObservedSequence);
        }
        return appliedSequence;
    }
    public static final class StaleReplicaException extends RuntimeException {
        public StaleReplicaException(long applied, long required) {
            super("Replica applied " + applied + " but session requires " + required);
        }
    }
}
