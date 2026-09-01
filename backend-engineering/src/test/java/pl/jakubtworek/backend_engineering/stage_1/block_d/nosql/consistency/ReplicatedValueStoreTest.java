package pl.jakubtworek.backend_engineering.stage_1.block_d.nosql.consistency;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReplicatedValueStoreTest {

    @Test
    void shouldMakeReplicationLagAndReadYourWritesVisible() {
        ReplicatedValueStore<String> store = new ReplicatedValueStore<>("old-email@example.com");

        ReplicatedValueStore.ConsistencyToken token = store.write("new-email@example.com");

        assertThat(store.readReplica().value()).isEqualTo("old-email@example.com");
        assertThat(store.readYourWrites(token).value()).isEqualTo("new-email@example.com");
        assertThat(store.pendingReplications()).isOne();

        store.replicateNext();

        assertThat(store.readReplica()).isEqualTo(store.readLeader());
        assertThat(store.pendingReplications()).isZero();
    }

    @Test
    void shouldReplicateVersionsInOrder() {
        ReplicatedValueStore<String> store = new ReplicatedValueStore<>("v0");
        store.write("v1");
        store.write("v2");

        store.replicateNext();
        assertThat(store.readReplica()).isEqualTo(new VersionedValue<>("v1", 1));

        store.replicateNext();
        assertThat(store.readReplica()).isEqualTo(new VersionedValue<>("v2", 2));
    }

    @Test
    void shouldFailInsteadOfReturningAValueOlderThanTheSessionToken() {
        ReplicatedValueStore<String> store = new ReplicatedValueStore<>("v0");

        assertThatThrownBy(() -> store.readYourWrites(new ReplicatedValueStore.ConsistencyToken(5)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot satisfy");
    }
}
