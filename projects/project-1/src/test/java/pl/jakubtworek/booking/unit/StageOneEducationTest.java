package pl.jakubtworek.booking.unit;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.booking.education.concurrency.VirtualThreadReservationBatch;
import pl.jakubtworek.booking.education.network.DownstreamTimeoutPolicy;
import pl.jakubtworek.booking.education.refactoring.ReservationFeePolicy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class StageOneEducationTest {

    @Test
    void virtualThreadsDoNotRemoveTheDownstreamConcurrencyLimit() throws InterruptedException {
        AtomicInteger active = new AtomicInteger();
        AtomicInteger observedMaximum = new AtomicInteger();
        VirtualThreadReservationBatch batch = new VirtualThreadReservationBatch(2);

        List<Callable<Integer>> operations = java.util.stream.IntStream.range(0, 12)
                .mapToObj(index -> (Callable<Integer>) () -> {
                    int current = active.incrementAndGet();
                    observedMaximum.accumulateAndGet(current, Math::max);
                    try {
                        Thread.sleep(Duration.ofMillis(10));
                        return index;
                    } finally {
                        active.decrementAndGet();
                    }
                })
                .toList();

        assertThat(batch.execute(operations)).containsExactlyElementsOf(
                java.util.stream.IntStream.range(0, 12).boxed().toList());
        assertThat(observedMaximum).hasValue(2);
    }

    @Test
    void timeoutHierarchyAndRetryAmplificationAreExplicit() {
        DownstreamTimeoutPolicy policy = new DownstreamTimeoutPolicy(
                Duration.ofMillis(100), Duration.ofMillis(300), Duration.ofMillis(50), 2);

        assertThat(policy.worstCaseClientBudget()).isEqualTo(Duration.ofMillis(850));
        assertThat(policy.fitsWithin(Duration.ofSeconds(1), Duration.ofSeconds(2))).isTrue();
        assertThat(DownstreamTimeoutPolicy.amplifiedAttempts(2, 2, 2)).isEqualTo(8);
    }

    @Test
    void injectedClockMakesLegacyBoundaryAndRoundingReproducible() {
        Clock clock = Clock.fixed(Instant.parse("2026-06-01T10:00:00Z"), ZoneOffset.UTC);
        ReservationFeePolicy policy = new ReservationFeePolicy(clock);

        assertThat(policy.fee(new BigDecimal("19.90"), LocalDate.of(2026, 7, 1)))
                .isEqualByComparingTo("0.40");
        assertThat(policy.fee(new BigDecimal("19.90"), LocalDate.of(2026, 6, 30)))
                .isEqualByComparingTo("1.00");
    }
}
