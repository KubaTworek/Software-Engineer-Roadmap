package pl.jakubtworek.backend_engineering.stage_3.block_b.alerts.example;

import pl.jakubtworek.backend_engineering.stage_3.block_b.alerts.*;
import pl.jakubtworek.backend_engineering.stage_3.block_b.alerts.runbook.CheckoutRunbooks;
import pl.jakubtworek.backend_engineering.stage_3.block_b.alerts.runbook.RunbookMarkdownRenderer;

import java.util.List;

/**
 * Demonstrates generation of alerting and runbook artifacts.
 *
 * The same source model can produce Prometheus rules, Alertmanager routing,
 * and Markdown runbooks, which helps keep operational documentation consistent.
 */
public final class AlertingExample {

    public static void main(String[] args) {
        PrometheusRuleGroup ruleGroup = new PrometheusRuleGroup(
                "checkout-api-alerts",
                List.of(
                        CheckoutApiAlertRules.highRequestLatencyP95(),
                        CheckoutApiAlertRules.highErrorRate5xx(),
                        CheckoutApiAlertRules.databaseConnectionTimeouts(),
                        CheckoutApiAlertRules.redisErrorsOrTimeouts()
                )
        );

        String prometheusRulesYaml =
                new PrometheusRuleYamlRenderer().render(ruleGroup);

        String alertmanagerYaml =
                new AlertmanagerYamlRenderer().render(AlertmanagerConfig.checkoutDefault());

        RunbookMarkdownRenderer runbookRenderer = new RunbookMarkdownRenderer();

        String latencyRunbook =
                runbookRenderer.render(CheckoutRunbooks.latencySpike());

        String dbRunbook =
                runbookRenderer.render(CheckoutRunbooks.databaseDown());

        String redisRunbook =
                runbookRenderer.render(CheckoutRunbooks.redisDown());

        System.out.println(prometheusRulesYaml);
        System.out.println(alertmanagerYaml);
        System.out.println(latencyRunbook);
        System.out.println(dbRunbook);
        System.out.println(redisRunbook);
    }
}