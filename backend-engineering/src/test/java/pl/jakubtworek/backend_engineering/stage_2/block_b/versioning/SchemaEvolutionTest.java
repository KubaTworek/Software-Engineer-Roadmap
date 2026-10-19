package pl.jakubtworek.backend_engineering.stage_2.block_b.versioning;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.replay.ReplayMode;
import pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.replay.ReplayRequest;
import pl.jakubtworek.backend_engineering.stage_2.block_b.versioning.schema.CompatibilityMode;
import pl.jakubtworek.backend_engineering.stage_2.block_b.versioning.schema.EventSchemaPolicy;
import pl.jakubtworek.backend_engineering.stage_2.block_b.versioning.schema.SchemaChange;
import pl.jakubtworek.backend_engineering.stage_2.block_b.versioning.schema.SchemaChangeType;
import pl.jakubtworek.backend_engineering.stage_2.block_b.versioning.schema.SchemaEvolutionReview;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaEvolutionTest {

    @Test
    void shouldAcceptOptionalOrDefaultedFieldAndRejectBreakingChanges() {
        EventSchemaPolicy policy = new EventSchemaPolicy();

        assertTrue(policy.validate("OrderPlaced", List.of(
                new SchemaChange("couponCode", SchemaChangeType.FIELD_ADDED, false, true),
                new SchemaChange("channel", SchemaChangeType.FIELD_ADDED, true, false)
        )).compatible());
        assertFalse(policy.validate("OrderPlaced", List.of(
                new SchemaChange("totalAmount", SchemaChangeType.FIELD_REMOVED, false, false)
        )).compatible());
        assertFalse(policy.validate("OrderPlaced", List.of(
                new SchemaChange("requiredField", SchemaChangeType.FIELD_ADDED, false, false)
        )).compatible());
    }

    @Test
    void shouldRequireMigrationPlanForUnsafeEvolutionReview() {
        SchemaEvolutionReview review = new SchemaEvolutionReview(CompatibilityMode.FULL);

        assertTrue(review.review(List.of(
                new SchemaChange("description", SchemaChangeType.DOCUMENTATION_CHANGED, false, false)
        )).compatible());
        assertFalse(review.review(List.of(
                new SchemaChange("amount", SchemaChangeType.FIELD_TYPE_CHANGED, false, false)
        )).compatible());
    }

    @Test
    void shouldValidateReplayModeAgainstTimestamp() {
        Instant timestamp = Instant.parse("2026-01-01T00:00:00Z");

        new ReplayRequest("orders", ReplayMode.FROM_BEGINNING, null);
        new ReplayRequest("orders", ReplayMode.FROM_TIMESTAMP, timestamp);
        assertThrows(IllegalArgumentException.class, () ->
                new ReplayRequest("orders", ReplayMode.FROM_TIMESTAMP, null));
        assertThrows(IllegalArgumentException.class, () ->
                new ReplayRequest("orders", ReplayMode.FROM_BEGINNING, timestamp));
    }
}
