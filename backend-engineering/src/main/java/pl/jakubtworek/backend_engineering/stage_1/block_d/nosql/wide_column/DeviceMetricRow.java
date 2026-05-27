package pl.jakubtworek.backend_engineering.stage_1.block_d.nosql.wide_column;

import java.time.Instant;
import java.util.Map;

/**
 * Przykład danych time-series pod wide-column database.
 *
 * Partition key:
 * - device_id
 * - bucket_day
 *
 * Clustering key:
 * - metric_time
 */
public class DeviceMetricRow {

    private final String deviceId;
    private final String bucketDay;
    private final Instant metricTime;
    private final double temperature;
    private final double batteryLevel;
    private final Map<String, String> attributes;

    public DeviceMetricRow(
            String deviceId,
            String bucketDay,
            Instant metricTime,
            double temperature,
            double batteryLevel,
            Map<String, String> attributes
    ) {
        this.deviceId = deviceId;
        this.bucketDay = bucketDay;
        this.metricTime = metricTime;
        this.temperature = temperature;
        this.batteryLevel = batteryLevel;
        this.attributes = Map.copyOf(attributes);
    }

    public static String partitionKey(String deviceId, String bucketDay) {
        return deviceId + "#" + bucketDay;
    }

    public String deviceId() { return deviceId; }
    public String bucketDay() { return bucketDay; }
    public Instant metricTime() { return metricTime; }
    public double temperature() { return temperature; }
    public double batteryLevel() { return batteryLevel; }
    public Map<String, String> attributes() { return attributes; }
}
