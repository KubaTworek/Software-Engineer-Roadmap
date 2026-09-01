package pl.jakubtworek.backend_engineering.stage_1.block_d.nosql.modeling;

import java.util.List;
import java.util.Set;

import static pl.jakubtworek.backend_engineering.stage_1.block_d.nosql.modeling.AccessPattern.ConsistencyRequirement.EVENTUAL;
import static pl.jakubtworek.backend_engineering.stage_1.block_d.nosql.modeling.AccessPattern.OperationType.READ;

/** Gotowe decyzje projektowe, które można skonfrontować z nowymi query shapes. */
public final class AccessPatternDesignCatalog {

    private AccessPatternDesignCatalog() {
    }

    public static AccessPatternDesign ordersByUserAndStatus() {
        AccessPattern pattern = new AccessPattern(
                "Get user orders for status ordered newest first",
                READ,
                "userId + status",
                List.of("userId", "status"),
                List.of("createdAt DESC", "orderId"),
                EVENTUAL
        );
        AccessPatternDesign.TableSchema table = new AccessPatternDesign.TableSchema(
                "orders_by_user_status",
                List.of("userId", "status"),
                List.of("createdAt DESC", "orderId"),
                Set.of("orderId", "userId", "status", "createdAt", "totalAmount"),
                10_000
        );
        return new AccessPatternDesign(pattern, table);
    }

    public static AccessPatternDesign metricsByDeviceAndDay() {
        AccessPattern pattern = new AccessPattern(
                "Get device metrics for one day ordered newest first",
                READ,
                "deviceId + bucketDay",
                List.of("deviceId", "bucketDay", "metricTime range"),
                List.of("metricTime DESC"),
                EVENTUAL
        );
        AccessPatternDesign.TableSchema table = new AccessPatternDesign.TableSchema(
                "metrics_by_device_day",
                List.of("deviceId", "bucketDay"),
                List.of("metricTime DESC"),
                Set.of("deviceId", "bucketDay", "metricTime", "temperature", "batteryLevel"),
                86_400
        );
        return new AccessPatternDesign(pattern, table);
    }
}
