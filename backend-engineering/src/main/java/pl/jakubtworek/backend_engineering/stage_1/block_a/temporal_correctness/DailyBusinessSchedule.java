package pl.jakubtworek.backend_engineering.stage_1.block_a.temporal_correctness;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;

/** A calendar schedule: "every day at local time", not "every 24 hours". */
public final class DailyBusinessSchedule {

    private final LocalTime localTime;
    private final ZoneId zone;
    private final LocalScheduleResolver.GapPolicy gapPolicy;
    private final LocalScheduleResolver.OverlapPolicy overlapPolicy;
    private final LocalScheduleResolver resolver = new LocalScheduleResolver();

    public DailyBusinessSchedule(
            LocalTime localTime,
            ZoneId zone,
            LocalScheduleResolver.GapPolicy gapPolicy,
            LocalScheduleResolver.OverlapPolicy overlapPolicy
    ) {
        this.localTime = Objects.requireNonNull(localTime);
        this.zone = Objects.requireNonNull(zone);
        this.gapPolicy = Objects.requireNonNull(gapPolicy);
        this.overlapPolicy = Objects.requireNonNull(overlapPolicy);
    }

    public Instant nextAfter(Instant instant) {
        Objects.requireNonNull(instant, "instant must not be null");
        LocalDate candidateDate = instant.atZone(zone).toLocalDate();
        Instant candidate = resolve(candidateDate);
        if (!candidate.isAfter(instant)) {
            candidate = resolve(candidateDate.plusDays(1));
        }
        return candidate;
    }

    private Instant resolve(LocalDate date) {
        return resolver.resolve(LocalDateTime.of(date, localTime), zone, gapPolicy, overlapPolicy).toInstant();
    }
}
