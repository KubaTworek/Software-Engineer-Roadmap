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
        yaml.append("  receiver: ").append(quote(config.defaultReceiver())).append("\n");
        yaml.append("  group_by: [")
                .append(config.groupBy().stream().map(AlertmanagerYamlRenderer::quote).collect(java.util.stream.Collectors.joining(", ")))
                .append("]\n");
        yaml.append("  group_wait: ").append(quote(config.groupWait())).append("\n");
        yaml.append("  group_interval: ").append(quote(config.groupInterval())).append("\n");
        yaml.append("  repeat_interval: ").append(quote(config.repeatInterval())).append("\n");
        yaml.append("  routes:\n");

        for (AlertmanagerRoute route : config.routes()) {
            yaml.append("    - receiver: ").append(quote(route.receiver())).append("\n");
            yaml.append("      matchers:\n");

            for (String matcher : route.matchers()) {
                yaml.append("        - ").append(quote(matcher)).append("\n");
            }
        }

        yaml.append("\nreceivers:\n");

        for (String receiver : config.receivers()) {
            yaml.append("  - name: ").append(quote(receiver)).append("\n");
        }

        return yaml.toString();
    }

    private static String quote(String value) {
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t") + "\"";
    }
}
