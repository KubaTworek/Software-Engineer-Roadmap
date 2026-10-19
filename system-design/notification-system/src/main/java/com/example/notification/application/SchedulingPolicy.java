package com.example.notification.application;

import org.springframework.stereotype.Component;

import java.time.*;
import java.time.zone.ZoneOffsetTransition;
import java.util.List;

/** Jawna polityka zamiany czasu lokalnego na jednoznaczny Instant oraz obsługi misfire. */
@Component
public class SchedulingPolicy {
    public enum OverlapPolicy { EARLIER_OFFSET, LATER_OFFSET }
    public enum MisfirePolicy { SEND_NOW, SKIP, EXPIRE }

    private final Clock clock;
    public SchedulingPolicy(Clock clock) { this.clock = clock; }

    public Instant resolve(LocalDate date, LocalTime time, ZoneId zone, OverlapPolicy overlapPolicy) {
        LocalDateTime local = LocalDateTime.of(date, time);
        List<ZoneOffset> offsets = zone.getRules().getValidOffsets(local);
        if (offsets.size() == 1) return local.toInstant(offsets.getFirst());
        if (offsets.size() == 2) return local.toInstant(overlapPolicy == OverlapPolicy.EARLIER_OFFSET
                ? offsets.getFirst() : offsets.getLast());
        ZoneOffsetTransition gap = zone.getRules().getTransition(local);
        return gap.getDateTimeAfter().toInstant(gap.getOffsetAfter());
    }

    public boolean shouldDispatch(Instant scheduledAt, Instant expiresAt, MisfirePolicy policy) {
        Instant now = clock.instant();
        if (expiresAt != null && !expiresAt.isAfter(now)) return false;
        if (!scheduledAt.isBefore(now)) return true;
        return policy == MisfirePolicy.SEND_NOW;
    }
}
