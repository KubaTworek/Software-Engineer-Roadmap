package pl.jakubtworek.backend_engineering.stage_1.block_d.nosql.modeling;

import java.util.List;

import static pl.jakubtworek.backend_engineering.stage_1.block_d.nosql.modeling.AccessPattern.ConsistencyRequirement.EVENTUAL;
import static pl.jakubtworek.backend_engineering.stage_1.block_d.nosql.modeling.AccessPattern.ConsistencyRequirement.STRONG;
import static pl.jakubtworek.backend_engineering.stage_1.block_d.nosql.modeling.AccessPattern.OperationType.READ;
import static pl.jakubtworek.backend_engineering.stage_1.block_d.nosql.modeling.AccessPattern.OperationType.UPDATE;
import static pl.jakubtworek.backend_engineering.stage_1.block_d.nosql.modeling.AccessPattern.OperationType.WRITE;

/**
 * Katalog przykładowych access patternów.
 */
public final class AccessPatternCatalog {

    private AccessPatternCatalog() {
    }

    public static List<AccessPattern> examples() {
        return List.of(
                new AccessPattern(
                        "Get user orders ordered by creation time",
                        READ,
                        "userId",
                        List.of("status"),
                        List.of("createdAt DESC"),
                        EVENTUAL
                ),
                new AccessPattern(
                        "Create payment transaction",
                        WRITE,
                        "paymentId",
                        List.of("orderId"),
                        List.of(),
                        STRONG
                ),
                new AccessPattern(
                        "Decrease product stock",
                        UPDATE,
                        "productId",
                        List.of(),
                        List.of(),
                        STRONG
                ),
                new AccessPattern(
                        "Get user session",
                        READ,
                        "sessionId",
                        List.of(),
                        List.of(),
                        EVENTUAL
                ),
                new AccessPattern(
                        "Get device metrics for day",
                        READ,
                        "deviceId + bucketDay",
                        List.of("metricTime range"),
                        List.of("metricTime DESC"),
                        EVENTUAL
                )
        );
    }
}
