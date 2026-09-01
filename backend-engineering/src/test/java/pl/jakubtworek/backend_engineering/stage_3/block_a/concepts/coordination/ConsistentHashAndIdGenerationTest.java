package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.coordination;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsistentHashAndIdGenerationTest {

    private static final Instant ID_EPOCH = Instant.parse("2025-01-01T00:00:00Z");

    @Test
    void nodeAndSequenceBitsMakeIdsUniqueWithinTheSameMillisecond() {
        Clock instant = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        SnowflakeIdGenerator nodeSeven = new SnowflakeIdGenerator(7, ID_EPOCH, instant);
        SnowflakeIdGenerator nodeEight = new SnowflakeIdGenerator(8, ID_EPOCH, instant);

        long first = nodeSeven.nextId();
        long second = nodeSeven.nextId();
        long otherNode = nodeEight.nextId();

        assertThat(first).isLessThan(second);
        assertThat(otherNode).isNotIn(first, second);
        assertThat(nodeSeven.decode(second))
                .isEqualTo(new SnowflakeIdComponents(instant.instant(), 7, 1));
        assertThat(nodeEight.decode(otherNode).nodeId()).isEqualTo(8);
    }

    @Test
    void generatorFailsFastWhenItsClockMovesBackwards() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:01Z"));
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1, ID_EPOCH, clock);
        generator.nextId();

        clock.set(Instant.parse("2026-01-01T00:00:00Z"));

        assertThatThrownBy(generator::nextId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("clock moved backwards");
    }

    @Test
    void generatorDoesNotWrapItsSequenceWithinOneMillisecond() {
        Clock instant = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1, ID_EPOCH, instant);

        IntStream.range(0, 4_096).forEach(ignored -> generator.nextId());

        assertThatThrownBy(generator::nextId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sequence exhausted");
    }

    @Test
    void addingANodeMovesOnlyItsShareOfKeys() {
        ConsistentHashRing ring = new ConsistentHashRing(200);
        ring.addNode("node-a");
        ring.addNode("node-b");
        ring.addNode("node-c");
        Map<String, String> before = assignments(ring, 10_000);

        ring.addNode("node-d");
        Map<String, String> after = assignments(ring, 10_000);

        long moved = before.keySet().stream()
                .filter(key -> !before.get(key).equals(after.get(key)))
                .count();
        double movedRatio = moved / 10_000.0;

        assertThat(movedRatio).isBetween(0.15, 0.35);
        assertThat(after.entrySet())
                .filteredOn(entry -> !entry.getValue().equals(before.get(entry.getKey())))
                .allMatch(entry -> entry.getValue().equals("node-d"));
    }

    @Test
    void removingANodeMovesOnlyKeysPreviouslyOwnedByThatNode() {
        ConsistentHashRing ring = new ConsistentHashRing(200);
        ring.addNode("node-a");
        ring.addNode("node-b");
        ring.addNode("node-c");
        Map<String, String> before = assignments(ring, 10_000);

        ring.removeNode("node-b");
        Map<String, String> after = assignments(ring, 10_000);

        assertThat(after).doesNotContainValue("node-b");
        before.forEach((key, previousOwner) -> {
            if (!previousOwner.equals("node-b")) {
                assertThat(after.get(key)).isEqualTo(previousOwner);
            }
        });
    }

    private static Map<String, String> assignments(ConsistentHashRing ring, int keyCount) {
        Map<String, String> assignments = new HashMap<>();
        IntStream.range(0, keyCount).forEach(index -> {
            String key = "customer-" + index;
            assignments.put(key, ring.ownerOf(key).orElseThrow());
        });
        return assignments;
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        void set(Instant instant) {
            current = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
