package pl.jakubtworek.backend_engineering.stage_1.block_a.temporal_correctness;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;

public final class TimeRepresentation {

    private TimeRepresentation() {
    }

    /** Persist the instant; choose the user's current zone only at presentation time. */
    public static ZonedDateTime present(Instant storedInstant, ZoneId userZone) {
        return Objects.requireNonNull(storedInstant).atZone(Objects.requireNonNull(userZone));
    }

    /** LocalDateTime alone cannot identify an instant until zone rules and DST policy are supplied. */
    public static Instant resolveBusinessTime(
            LocalDateTime localDateTime,
            ZoneId businessZone,
            LocalScheduleResolver.GapPolicy gapPolicy,
            LocalScheduleResolver.OverlapPolicy overlapPolicy
    ) {
        return new LocalScheduleResolver()
                .resolve(localDateTime, businessZone, gapPolicy, overlapPolicy)
                .toInstant();
    }
}
