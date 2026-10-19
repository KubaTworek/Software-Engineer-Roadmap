package pl.jakubtworek.backend_engineering.stage_2.block_c.progressive_delivery;

/** Comparable metrics from the same window and traffic class. */
public record ServiceMetrics(long requests, long errors, double p99Millis) {

    public ServiceMetrics {
        if (requests < 0 || errors < 0 || errors > requests) {
            throw new IllegalArgumentException("errors must be between zero and requests");
        }
        if (!Double.isFinite(p99Millis) || p99Millis < 0) {
            throw new IllegalArgumentException("p99Millis must be finite and non-negative");
        }
    }

    public double errorRate() {
        return requests == 0 ? 0.0 : (double) errors / requests;
    }
}
