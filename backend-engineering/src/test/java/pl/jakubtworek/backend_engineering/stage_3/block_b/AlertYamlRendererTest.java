package pl.jakubtworek.backend_engineering.stage_3.block_b;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import pl.jakubtworek.backend_engineering.stage_3.block_b.alerts.AlertAnnotations;
import pl.jakubtworek.backend_engineering.stage_3.block_b.alerts.AlertLabels;
import pl.jakubtworek.backend_engineering.stage_3.block_b.alerts.AlertSeverity;
import pl.jakubtworek.backend_engineering.stage_3.block_b.alerts.AlertmanagerConfig;
import pl.jakubtworek.backend_engineering.stage_3.block_b.alerts.AlertmanagerRoute;
import pl.jakubtworek.backend_engineering.stage_3.block_b.alerts.AlertmanagerYamlRenderer;
import pl.jakubtworek.backend_engineering.stage_3.block_b.alerts.PrometheusAlertRule;
import pl.jakubtworek.backend_engineering.stage_3.block_b.alerts.PrometheusRuleGroup;
import pl.jakubtworek.backend_engineering.stage_3.block_b.alerts.PrometheusRuleYamlRenderer;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlertYamlRendererTest {

    private final Yaml yamlParser = new Yaml();

    @Test
    void alertmanagerScalarsCannotEscapeIntoYamlStructure() {
        String receiver = "pager\nnew_root: injected";
        AlertmanagerConfig config = new AlertmanagerConfig(
                receiver,
                List.of("service"),
                "30s",
                "5m",
                "4h",
                List.of(new AlertmanagerRoute(receiver, List.of("severity=\"page\""))),
                List.of(receiver)
        );

        Map<String, Object> document = yamlParser.load(new AlertmanagerYamlRenderer().render(config));
        Map<String, Object> route = map(document.get("route"));
        List<Map<String, Object>> receivers = list(document.get("receivers"));

        assertThat(route.get("receiver")).isEqualTo(receiver);
        assertThat(receivers).singleElement().satisfies(item -> assertThat(item.get("name")).isEqualTo(receiver));
        assertThat(document).doesNotContainKey("new_root");
    }

    @Test
    void prometheusAnnotationsRoundTripWithQuotesAndNewlines() {
        String description = "Latency is high\nCheck key: \"value\" and C:\\logs";
        PrometheusAlertRule rule = new PrometheusAlertRule(
                "CheckoutLatencyHigh",
                "rate(checkout_errors_total[5m]) > 0.01",
                "5m",
                null,
                AlertLabels.builder()
                        .severity(AlertSeverity.PAGE)
                        .team("backend")
                        .service("checkout-api")
                        .build(),
                AlertAnnotations.builder()
                        .summary("Checkout latency")
                        .description(description)
                        .runbookUrl("https://example.test/runbook")
                        .build()
        );

        Map<String, Object> document = yamlParser.load(
                new PrometheusRuleYamlRenderer().render(new PrometheusRuleGroup("checkout", List.of(rule)))
        );
        List<Map<String, Object>> groups = list(document.get("groups"));
        List<Map<String, Object>> rules = list(groups.get(0).get("rules"));
        Map<String, Object> annotations = map(rules.get(0).get("annotations"));

        assertThat(annotations.get("description")).isEqualTo(description);
    }

    @Test
    void alertmanagerRejectsRoutesToUndeclaredReceivers() {
        assertThatThrownBy(() -> new AlertmanagerConfig(
                "default",
                List.of("service"),
                "30s",
                "5m",
                "4h",
                List.of(new AlertmanagerRoute("missing", List.of("severity=\"page\""))),
                List.of("default")
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> list(Object value) {
        return (List<Map<String, Object>>) value;
    }
}
