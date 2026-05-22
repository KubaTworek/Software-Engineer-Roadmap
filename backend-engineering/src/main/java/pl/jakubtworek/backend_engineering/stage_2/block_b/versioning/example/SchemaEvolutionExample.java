package pl.jakubtworek.backend_engineering.stage_2.block_b.versioning.example;

import pl.jakubtworek.backend_engineering.stage_2.block_b.versioning.schema.EventSchemaPolicy;
import pl.jakubtworek.backend_engineering.stage_2.block_b.versioning.schema.SchemaChange;
import pl.jakubtworek.backend_engineering.stage_2.block_b.versioning.schema.SchemaChangeType;
import pl.jakubtworek.backend_engineering.stage_2.block_b.versioning.schema.SchemaCompatibilityResult;

import java.util.List;

/**
 * Example showing how schema evolution can be reviewed before deployment.
 *
 * In a production system, this kind of check would typically run in CI together
 * with Schema Registry compatibility verification.
 */
public class SchemaEvolutionExample {

    public static void main(String[] args) {
        EventSchemaPolicy policy = new EventSchemaPolicy();

        List<SchemaChange> proposedChanges = List.of(
                new SchemaChange(
                        "customerId",
                        SchemaChangeType.FIELD_ADDED,
                        false,
                        true
                ),
                new SchemaChange(
                        "productName",
                        SchemaChangeType.FIELD_ADDED,
                        false,
                        true
                )
        );

        SchemaCompatibilityResult result = policy.validate(
                "OrderPlaced",
                proposedChanges
        );

        System.out.println(result.compatible());
        System.out.println(result.message());
    }
}