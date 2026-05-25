package pl.jakubtworek.backend_engineering.stage_3.block_b.alerts;

/**
 * Renders a minimal Alertmanager configuration as YAML.
 *
 * In production, prefer a proper YAML serializer or infrastructure-as-code template.
 */
public final class AlertmanagerYamlRenderer {

    public String render(AlertmanagerConfig config) {
        StringBuilder yaml = new StringBuilder();

        yaml.append("route:\n");
        yaml.append("  receiver: ").append(config.defaultReceiver()).append("\n");
        yaml.append("  group_by: [").append(String.join(", ", config.groupBy())).append("]\n");
        yaml.append("  group_wait: ").append(config.groupWait()).append("\n");
        yaml.append("  group_interval: ").append(config.groupInterval()).append("\n");
        yaml.append("  repeat_interval: ").append(config.repeatInterval()).append("\n");
        yaml.append("  routes:\n");

        for (AlertmanagerRoute route : config.routes()) {
            yaml.append("    - receiver: ").append(route.receiver()).append("\n");
            yaml.append("      matchers:\n");

            for (String matcher : route.matchers()) {
                yaml.append("        - ").append(matcher).append("\n");
            }
        }

        yaml.append("\nreceivers:\n");

        for (String receiver : config.receivers()) {
            yaml.append("  - name: ").append(receiver).append("\n");
        }

        return yaml.toString();
    }
}