package pl.jakubtworek.backend_engineering.stage_3.block_b.alerts;

import java.util.Map;

/**
 * Renders Prometheus alert rules as YAML.
 *
 * A production implementation would usually use a YAML library.
 * This renderer is intentionally simple to keep the alert model easy to understand.
 */
public final class PrometheusRuleYamlRenderer {

    public String render(PrometheusRuleGroup group) {
        StringBuilder yaml = new StringBuilder();

        yaml.append("groups:\n");
        yaml.append("- name: ").append(group.name()).append("\n");
        yaml.append("  rules:\n");

        for (PrometheusAlertRule rule : group.rules()) {
            yaml.append("  - alert: ").append(rule.alertName()).append("\n");
            yaml.append("    expr: |\n");
            indentBlock(yaml, rule.expression(), 6);
            yaml.append("    for: ").append(rule.forDuration()).append("\n");

            if (rule.keepFiringFor() != null && !rule.keepFiringFor().isBlank()) {
                yaml.append("    keep_firing_for: ").append(rule.keepFiringFor()).append("\n");
            }

            yaml.append("    labels:\n");
            appendMap(yaml, rule.labels().values(), 6);

            yaml.append("    annotations:\n");
            appendMap(yaml, rule.annotations().values(), 6);
        }

        return yaml.toString();
    }

    private static void appendMap(StringBuilder yaml, Map<String, String> values, int spaces) {
        String indent = " ".repeat(spaces);

        for (Map.Entry<String, String> entry : values.entrySet()) {
            yaml.append(indent)
                    .append(entry.getKey())
                    .append(": ")
                    .append(quote(entry.getValue()))
                    .append("\n");
        }
    }

    private static void indentBlock(StringBuilder yaml, String block, int spaces) {
        String indent = " ".repeat(spaces);

        for (String line : block.strip().split("\n")) {
            yaml.append(indent).append(line).append("\n");
        }
    }

    private static String quote(String value) {
        return "\"" + value.replace("\"", "\\\"") + "\"";
    }
}