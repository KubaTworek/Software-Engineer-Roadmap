package com.example.observability.server.metrics;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Wykonywalny model semantyki, której nie wolno zastąpić uniwersalnym avg.
 * Counter wymaga obsługi resetu, a histogram można scalać tylko wtedy, gdy
 * jego granice bucketów są identyczne. Percentyli nie wolno uśredniać.
 */
public final class MetricSemantics {
    private MetricSemantics() {}

    public static double counterRate(List<CounterPoint> points) {
        if (points == null || points.size() < 2) return 0;
        var ordered = points.stream().sorted(Comparator.comparing(CounterPoint::timestamp)).toList();
        double increase = 0;
        for (int i = 1; i < ordered.size(); i++) {
            double previous = ordered.get(i - 1).value();
            double current = ordered.get(i).value();
            // Po restarcie counter zaczyna od zera; aktualna wartość jest
            // przyrostem od resetu, a nie dużym ujemnym skokiem.
            increase += current >= previous ? current - previous : Math.max(0, current);
        }
        double seconds = Duration.between(ordered.getFirst().timestamp(), ordered.getLast().timestamp()).toNanos()
                / 1_000_000_000d;
        return seconds <= 0 ? 0 : increase / seconds;
    }

    public static Histogram merge(Histogram left, Histogram right) {
        if (!left.upperBounds().equals(right.upperBounds()))
            throw new IllegalArgumentException("histogram bucket boundaries differ");
        List<Long> counts = new ArrayList<>();
        for (int i = 0; i < left.cumulativeCounts().size(); i++)
            counts.add(left.cumulativeCounts().get(i) + right.cumulativeCounts().get(i));
        return new Histogram(left.upperBounds(), counts, left.count() + right.count(), left.sum() + right.sum());
    }

    /** Prometheus-style liniowa estymacja wewnątrz pierwszego bucketa z rangą q. */
    public static double quantile(Histogram histogram, double q) {
        if (q < 0 || q > 1 || histogram.count() == 0) return Double.NaN;
        double rank = q * histogram.count();
        long previousCount = 0;
        double lower = 0;
        for (int i = 0; i < histogram.upperBounds().size(); i++) {
            long cumulative = histogram.cumulativeCounts().get(i);
            if (cumulative >= rank) {
                double upper = histogram.upperBounds().get(i);
                long inBucket = cumulative - previousCount;
                return inBucket == 0 ? upper : lower + (upper - lower)
                        * ((rank - previousCount) / inBucket);
            }
            previousCount = cumulative;
            lower = histogram.upperBounds().get(i);
        }
        // Ranga w +Inf nie ma skończonej interpolacji; zwracamy ostatnią granicę.
        return histogram.upperBounds().isEmpty() ? Double.NaN : histogram.upperBounds().getLast();
    }

    public record CounterPoint(Instant timestamp, double value) {}
    public record Histogram(List<Double> upperBounds, List<Long> cumulativeCounts, long count, double sum) {
        public Histogram {
            upperBounds = List.copyOf(upperBounds);
            cumulativeCounts = List.copyOf(cumulativeCounts);
            if (upperBounds.size() + 1 != cumulativeCounts.size())
                throw new IllegalArgumentException("counts require explicit buckets plus +Inf");
            if (!cumulativeCounts.isEmpty() && cumulativeCounts.getLast() != count)
                throw new IllegalArgumentException("+Inf bucket must equal count");
        }
    }
}
