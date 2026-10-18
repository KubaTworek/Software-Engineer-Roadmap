package pl.jakubtworek.chatsystem.multiregion;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class MultiRegionConversationTest {
    @Test
    void fencingRejectsOldLeaderAndSessionTokenRejectsStaleRead() {
        UUID conversationId = UUID.randomUUID();
        ConversationLeadership leadership = new ConversationLeadership();
        RegionalConversationReplica replica = new RegionalConversationReplica(conversationId, leadership);
        var warsaw = leadership.elect(conversationId, "eu-central");
        long first = replica.append(warsaw);
        var virginia = leadership.elect(conversationId, "us-east");

        assertThatThrownBy(() -> replica.append(warsaw))
                .isInstanceOf(ConversationLeadership.StaleLeaderException.class);
        long second = replica.append(virginia);
        replica.replicateThrough(first);
        assertThatThrownBy(() -> replica.read(second))
                .isInstanceOf(RegionalConversationReplica.StaleReplicaException.class);
        replica.replicateThrough(second);
        assertThat(replica.read(second)).isEqualTo(second);
    }
}
