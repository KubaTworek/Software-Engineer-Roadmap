package com.ridesharing.region;

import java.time.Instant;

public record RoutingDecision(String aggregateId, String homeRegion, String routedRegion, String consistencyMode, String reason, Instant decidedAt) {}
