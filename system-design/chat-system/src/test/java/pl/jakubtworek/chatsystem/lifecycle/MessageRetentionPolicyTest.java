package pl.jakubtworek.chatsystem.lifecycle;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class MessageRetentionPolicyTest {
    private final Instant deletion = Instant.parse("2026-01-01T00:00:00Z");
    private final MessageRetentionPolicy policy = new MessageRetentionPolicy(Duration.ofDays(30));

    @Test
    void physicalDeletionWaitsForDevicesBackupsAndLegalHold() {
        assertThat(policy.decide(context(List.of(10L), false, deletion.plus(Duration.ofDays(60))),
                deletion.plus(Duration.ofDays(31))))
                .isEqualTo(MessageRetentionPolicy.Decision.ERASE_PRIMARY_CONTENT);
        assertThat(policy.decide(context(List.of(9L), false, deletion), deletion.plus(Duration.ofDays(31))))
                .isEqualTo(MessageRetentionPolicy.Decision.ERASE_PRIMARY_CONTENT);
        assertThat(policy.decide(context(List.of(10L), true, deletion), deletion.plus(Duration.ofDays(365))))
                .isEqualTo(MessageRetentionPolicy.Decision.KEEP_TOMBSTONE);
        assertThat(policy.decide(context(List.of(10L), false, deletion), deletion.plus(Duration.ofDays(31))))
                .isEqualTo(MessageRetentionPolicy.Decision.PHYSICALLY_DELETE);
    }

    private MessageRetentionPolicy.Context context(List<Long> cursors, boolean hold, Instant backupUntil) {
        return new MessageRetentionPolicy.Context(10, deletion, cursors, hold, backupUntil);
    }
}
