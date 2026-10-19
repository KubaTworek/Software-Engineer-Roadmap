package pl.jakubtworek.backend_engineering.stage_2.block_b.observability;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.backend_engineering.stage_2.block_b.observability.alerting.AlertSeverity;
import pl.jakubtworek.backend_engineering.stage_2.block_b.observability.alerting.ConsumerLagAlertRule;
import pl.jakubtworek.backend_engineering.stage_2.block_b.observability.alerting.DeadLetterQueueAlertRule;
import pl.jakubtworek.backend_engineering.stage_2.block_b.observability.alerting.OutboxAlertRule;
import pl.jakubtworek.backend_engineering.stage_2.block_b.observability.kafka.ConsumerLag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertRulesTest {

    @Test
    void shouldRaiseLagAlertOnlyAboveThreshold() {
        ConsumerLagAlertRule rule = new ConsumerLagAlertRule(100);

        assertFalse(rule.evaluate(new ConsumerLag("payments", "orders", 0, 900, 1000)).isPresent());
        assertTrue(rule.evaluate(new ConsumerLag("payments", "orders", 0, 899, 1000)).isPresent());
    }

    @Test
    void shouldPrioritizeCriticalOutboxAgeOverPendingCountWarning() {
        OutboxAlertRule rule = new OutboxAlertRule(10, 60);

        var alert = rule.evaluate(11, 61).orElseThrow();

        assertEquals(AlertSeverity.CRITICAL, alert.severity());
        assertEquals("Outbox oldest event too old", alert.name());
    }

    @Test
    void shouldRepresentHealthyStateWithoutNullAndValidateMeasurements() {
        assertTrue(new OutboxAlertRule(10, 60).evaluate(0, 0).isEmpty());
        assertTrue(new DeadLetterQueueAlertRule(0).evaluate("orders.dlq", 0).isEmpty());
        assertThrows(IllegalArgumentException.class, () ->
                new DeadLetterQueueAlertRule(10).evaluate("orders.dlq", -1));
        assertThrows(IllegalArgumentException.class, () ->
                new ConsumerLag("payments", "orders", -1, 0, 0));
    }
}
