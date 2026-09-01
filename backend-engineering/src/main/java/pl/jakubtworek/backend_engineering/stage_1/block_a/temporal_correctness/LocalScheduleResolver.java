package pl.jakubtworek.backend_engineering.stage_1.block_a.temporal_correctness;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneRules;
import java.util.List;
import java.util.Objects;

/** Resolves a local business time only after gap and overlap policies are explicit. */
public final class LocalScheduleResolver {

    public ZonedDateTime resolve(
            LocalDateTime localDateTime,
            ZoneId zone,
            GapPolicy gapPolicy,
            OverlapPolicy overlapPolicy
    ) {
        Objects.requireNonNull(localDateTime, "localDateTime must not be null");
        Objects.requireNonNull(zone, "zone must not be null");
        Objects.requireNonNull(gapPolicy, "gapPolicy must not be null");
        Objects.requireNonNull(overlapPolicy, "overlapPolicy must not be null");

        ZoneRules rules = zone.getRules();
        List<ZoneOffset> offsets = rules.getValidOffsets(localDateTime);
        if (offsets.size() == 1) {
            return ZonedDateTime.ofLocal(localDateTime, zone, offsets.getFirst());
        }
        if (offsets.size() == 2) {
            ZoneOffset selected = overlapPolicy == OverlapPolicy.EARLIER_OFFSET
                    ? offsets.getFirst()
                    : offsets.getLast();
            return ZonedDateTime.ofLocal(localDateTime, zone, selected);
        }

        ZoneOffsetTransition transition = rules.getTransition(localDateTime);
        if (gapPolicy == GapPolicy.REJECT) {
            throw new DateTimeException(localDateTime + " does not exist in " + zone);
        }
        LocalDateTime shifted = localDateTime.plus(transition.getDuration());
        return ZonedDateTime.ofLocal(shifted, zone, transition.getOffsetAfter());
    }

    public enum GapPolicy {
        REJECT,
        SHIFT_FORWARD
    }

    public enum OverlapPolicy {
        EARLIER_OFFSET,
        LATER_OFFSET
    }
}
