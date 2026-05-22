package pl.jakubtworek.backend_engineering.stage_2.block_b.observability.alerting;

/**
 * Simple alert publisher used for demonstration.
 */
public class ConsoleAlertPublisher implements AlertPublisher {

    /**
     * Prints the alert to the console.
     */
    @Override
    public void publish(Alert alert) {
        System.out.println("ALERT: " + alert);
    }
}