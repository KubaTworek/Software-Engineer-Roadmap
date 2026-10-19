package pl.jakubtworek.backend_engineering.stage_2.block_b.observability.alerting;

import pl.jakubtworek.backend_engineering.stage_2.block_b.observability.kafka.ConsumerLag;

import java.util.Optional;

/**
 * Alert rule for excessive Kafka consumer lag.
 *
 * High lag means that consumers are falling behind and business processes
 * may be delayed.
 */
public class ConsumerLagAlertRule {

    private final long maxAllowedLag;

    public ConsumerLagAlertRule(long maxAllowedLag) {
        if (maxAllowedLag < 0) {
            throw new IllegalArgumentException("Maximum allowed lag cannot be negative");
        }
        this.maxAllowedLag = maxAllowedLag;
    }

    /**
     * Evaluates consumer lag and returns an alert when the threshold is exceeded.
     */
    public Optional<Alert> evaluate(ConsumerLag lag) {
        if (lag.lag() <= maxAllowedLag) {
            return Optional.empty();
        }

        return Optional.of(new Alert(
                "High Kafka consumer lag",
                AlertSeverity.CRITICAL,
                "Consumer group " + lag.consumerGroup()
                        + " has lag " + lag.lag()
                        + " on topic " + lag.topic()
                        + " partition " + lag.partition()
        ));
    }
}
