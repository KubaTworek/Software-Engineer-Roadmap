package pl.jakubtworek.backend_engineering.stage_1.block_d.nosql.consistency;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuorumConfigurationTest {

    @Test
    void shouldShowWhyNThreeRTwoWTwoHasOverlappingQuorums() {
        QuorumConfiguration quorum = new QuorumConfiguration(3, 2, 2);

        assertThat(quorum.readAndWriteQuorumsOverlap()).isTrue();
        assertThat(quorum.concurrentWriteQuorumsOverlap()).isTrue();
        assertThat(quorum.toleratedReadReplicaFailures()).isOne();
        assertThat(quorum.toleratedWriteReplicaFailures()).isOne();
    }

    @Test
    void shouldExposeTheAvailabilityTradeOffOfOneReplicaAcknowledgements() {
        QuorumConfiguration availableButWeak = new QuorumConfiguration(3, 1, 1);

        assertThat(availableButWeak.readAndWriteQuorumsOverlap()).isFalse();
        assertThat(availableButWeak.concurrentWriteQuorumsOverlap()).isFalse();
        assertThat(availableButWeak.toleratedReadReplicaFailures()).isEqualTo(2);
    }

    @Test
    void shouldRejectAnImpossibleQuorum() {
        assertThatThrownBy(() -> new QuorumConfiguration(3, 4, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("readAcks");
    }
}
