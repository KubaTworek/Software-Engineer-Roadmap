package pl.jakubtworek.backend_engineering.stage_3.block_b.alerts;

import java.util.List;
import java.util.Objects;

/**
 * Represents a minimal Alertmanager routing configuration.
 *
 * The root route defines default behavior, while child routes specialize routing
 * based on labels such as team, service, or severity.
 */
public final class AlertmanagerConfig {

    private final String defaultReceiver;
    private final List<String> groupBy;
    private final String groupWait;
    private final String groupInterval;
    private final String repeatInterval;
    private final List<AlertmanagerRoute> routes;
    private final List<String> receivers;

    public AlertmanagerConfig(
            String defaultReceiver,
            List<String> groupBy,
            String groupWait,
            String groupInterval,
            String repeatInterval,
            List<AlertmanagerRoute> routes,
            List<String> receivers
    ) {
        this.defaultReceiver = requireNonBlank(defaultReceiver, "defaultReceiver");
        this.groupBy = List.copyOf(Objects.requireNonNull(groupBy, "groupBy must not be null"));
        this.groupWait = requireNonBlank(groupWait, "groupWait");
        this.groupInterval = requireNonBlank(groupInterval, "groupInterval");
        this.repeatInterval = requireNonBlank(repeatInterval, "repeatInterval");
        this.routes = List.copyOf(Objects.requireNonNull(routes, "routes must not be null"));
        this.receivers = List.copyOf(Objects.requireNonNull(receivers, "receivers must not be null"));
    }

    public static AlertmanagerConfig checkoutDefault() {
        return new AlertmanagerConfig(
                "default-receiver",
                List.of("cluster", "service", "alertname"),
                "30s",
                "5m",
                "4h",
                List.of(
                        new AlertmanagerRoute(
                                "backend-pager",
                                List.of("team=\"backend\"", "severity=\"page\"")
                        ),
                        new AlertmanagerRoute(
                                "db-pager",
                                List.of("service=~\"postgres|mysql|cassandra\"")
                        ),
                        new AlertmanagerRoute(
                                "ticketing",
                                List.of("severity=\"ticket\"")
                        )
                ),
                List.of(
                        "default-receiver",
                        "backend-pager",
                        "db-pager",
                        "ticketing"
                )
        );
    }

    public String defaultReceiver() {
        return defaultReceiver;
    }

    public List<String> groupBy() {
        return groupBy;
    }

    public String groupWait() {
        return groupWait;
    }

    public String groupInterval() {
        return groupInterval;
    }

    public String repeatInterval() {
        return repeatInterval;
    }

    public List<AlertmanagerRoute> routes() {
        return routes;
    }

    public List<String> receivers() {
        return receivers;
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}