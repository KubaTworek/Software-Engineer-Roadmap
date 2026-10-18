package com.example.ratelimiter.algorithm.simulation;

import java.util.ArrayDeque;
import java.util.Deque;

/** Deterministic models compare semantics; the Redis adapter is the distributed implementation. */
public final class RateLimitAlgorithmLab {
    private RateLimitAlgorithmLab() {}

    /** Intentionally unsafe two-step counter used to demonstrate check-then-act races. */
    public static final class NaiveFixedWindow {
        private final long limit;
        private long used;
        public NaiveFixedWindow(long limit) { this.limit = limit; }
        public boolean check(long cost) { return used + cost <= limit; }
        public void commit(long cost) { used += cost; }
        public long used() { return used; }
    }

    public static final class FixedWindow {
        private final long limit;
        private final long windowMs;
        private long window = Long.MIN_VALUE;
        private long used;
        public FixedWindow(long limit, long windowMs) { this.limit = limit; this.windowMs = windowMs; }
        public synchronized boolean allow(long nowMs, long cost) {
            long candidate = Math.floorDiv(nowMs, windowMs);
            if (candidate != window) { window = candidate; used = 0; }
            if (used + cost > limit) return false;
            used += cost;
            return true;
        }
    }

    public static final class SlidingWindowLog {
        private final int limit;
        private final long windowMs;
        private final Deque<Long> accepted = new ArrayDeque<>();
        public SlidingWindowLog(int limit, long windowMs) { this.limit = limit; this.windowMs = windowMs; }
        public synchronized boolean allow(long nowMs) {
            while (!accepted.isEmpty() && accepted.peekFirst() <= nowMs - windowMs) accepted.removeFirst();
            if (accepted.size() >= limit) return false;
            accepted.addLast(nowMs);
            return true;
        }
        public int retainedEntries() { return accepted.size(); }
    }

    public static final class SlidingWindowCounter {
        private final long limit;
        private final long windowMs;
        private long currentWindow = Long.MIN_VALUE;
        private long current;
        private long previous;
        public SlidingWindowCounter(long limit, long windowMs) { this.limit = limit; this.windowMs = windowMs; }
        public synchronized boolean allow(long nowMs) {
            long candidate = Math.floorDiv(nowMs, windowMs);
            if (currentWindow == Long.MIN_VALUE) currentWindow = candidate;
            if (candidate > currentWindow) {
                previous = candidate == currentWindow + 1 ? current : 0;
                current = 0; currentWindow = candidate;
            }
            double elapsed = Math.floorMod(nowMs, windowMs) / (double) windowMs;
            double estimate = current + previous * (1.0 - elapsed);
            if (estimate + 1 > limit) return false;
            current++;
            return true;
        }
    }

    public static final class TokenBucket {
        private final double capacity;
        private final double refillPerMs;
        private double tokens;
        private long lastMs;
        public TokenBucket(long capacity, double refillPerSecond, long startMs) {
            this.capacity = capacity; this.refillPerMs = refillPerSecond / 1000.0;
            this.tokens = capacity; this.lastMs = startMs;
        }
        public synchronized boolean allow(long nowMs, long cost) {
            long elapsed = Math.max(0, nowMs - lastMs);
            tokens = Math.min(capacity, tokens + elapsed * refillPerMs);
            lastMs = Math.max(lastMs, nowMs);
            if (tokens < cost) return false;
            tokens -= cost;
            return true;
        }
        public double tokens() { return tokens; }
    }

    /**
     * Generic Cell Rate Algorithm stores one theoretical arrival time instead
     * of a token counter. It is equivalent to a continuously refilled bucket,
     * but its single scalar state is attractive for distributed stores.
     */
    public static final class Gcra {
        private final long intervalMs;
        private final long burstToleranceMs;
        private long theoreticalArrivalMs = Long.MIN_VALUE;

        public Gcra(long requestsPerSecond, long burst) {
            if (requestsPerSecond <= 0 || burst <= 0) throw new IllegalArgumentException("positive rate and burst required");
            this.intervalMs = Math.max(1, 1_000 / requestsPerSecond);
            this.burstToleranceMs = (burst - 1) * intervalMs;
        }

        public synchronized boolean allow(long nowMs) {
            long tat = theoreticalArrivalMs == Long.MIN_VALUE ? nowMs : theoreticalArrivalMs;
            if (nowMs < tat - burstToleranceMs) return false;
            theoreticalArrivalMs = Math.max(nowMs, tat) + intervalMs;
            return true;
        }
    }

    /**
     * Minimal AIMD concurrency controller. Rate limiting bounds arrivals;
     * concurrency limiting reacts to downstream saturation and bounds work in
     * flight. The two mechanisms protect different resources.
     */
    public static final class AdaptiveConcurrencyLimit {
        private final int maximum;
        private int limit;
        private int inFlight;

        public AdaptiveConcurrencyLimit(int initial, int maximum) {
            if (initial <= 0 || maximum < initial) throw new IllegalArgumentException("invalid limits");
            this.limit = initial;
            this.maximum = maximum;
        }

        public synchronized boolean tryAcquire() {
            if (inFlight >= limit) return false;
            inFlight++;
            return true;
        }

        public synchronized void complete(boolean overloaded) {
            if (inFlight <= 0) throw new IllegalStateException("completion without acquisition");
            inFlight--;
            limit = overloaded ? Math.max(1, limit / 2) : Math.min(maximum, limit + 1);
        }

        public int limit() { return limit; }
    }

    /** Leaky bucket shapes queued work; denial means the finite queue is full. */
    public static final class LeakyBucket {
        private final double capacity;
        private final double leakPerMs;
        private double queued;
        private long lastMs;
        public LeakyBucket(long capacity, double leakPerSecond, long startMs) {
            this.capacity = capacity; this.leakPerMs = leakPerSecond / 1000.0; this.lastMs = startMs;
        }
        public synchronized boolean enqueue(long nowMs, long cost) {
            queued = Math.max(0, queued - Math.max(0, nowMs - lastMs) * leakPerMs);
            lastMs = Math.max(lastMs, nowMs);
            if (queued + cost > capacity) return false;
            queued += cost;
            return true;
        }
        public double queued() { return queued; }
    }
}
