package com.ridesharing.realtime;

import java.time.Instant;
import java.util.Map;

public record RealtimeEvent(
        String type,
        String aggregateId,
        String cityId,
        Map<String, Object> payload,
        Instant emittedAt
) {}
