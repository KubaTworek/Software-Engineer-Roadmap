package pl.jakubtworek.backend_engineering.stage_2.block_b.observability.alerting;

/**
 * Sends alerts to an external notification channel.
 *
 * Production implementations may integrate with PagerDuty, Slack,
 * email, Opsgenie or another incident management system.
 */
public interface AlertPublisher {

    /**
     * Publishes an alert.
     */
    void publish(Alert alert);
}