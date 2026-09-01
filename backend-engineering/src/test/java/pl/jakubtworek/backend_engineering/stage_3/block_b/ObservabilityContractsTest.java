package pl.jakubtworek.backend_engineering.stage_3.block_b;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.backend_engineering.stage_3.block_b.prometheus.MetricCardinalityGuard;
import pl.jakubtworek.backend_engineering.stage_3.block_b.prometheus.RouteTemplate;
import pl.jakubtworek.backend_engineering.stage_3.block_b.structured_logs.CorrelationContext;
import pl.jakubtworek.backend_engineering.stage_3.block_b.structured_logs.LogSeverity;
import pl.jakubtworek.backend_engineering.stage_3.block_b.structured_logs.ServiceResource;
import pl.jakubtworek.backend_engineering.stage_3.block_b.structured_logs.StructuredLogEvent;
import pl.jakubtworek.backend_engineering.stage_3.block_b.structured_logs.StructuredLogJsonSerializer;
import pl.jakubtworek.backend_engineering.stage_3.block_b.tracing.RequestCorrelation;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObservabilityContractsTest {

    @Test
    void structuredEventContainsStableCorrelationAndSerializesToJson() {
        StructuredLogEvent event = StructuredLogEvent.builder(resource())
                .severity(LogSeverity.INFO)
                .eventName("checkout.completed")
                .body("Checkout completed")
                .correlation(new CorrelationContext(
                        "req-123",
                        "4bf92f3577b34da6a3ce929d0e0e4736",
                        "00f067aa0ba902b7"
                ))
                .attribute("order.status", "paid")
                .build();

        String json = new StructuredLogJsonSerializer(new ObjectMapper()).toJson(event);

        assertThat(json)
                .contains("\"service.name\":\"checkout-api\"")
                .contains("\"event.name\":\"checkout.completed\"")
                .contains("\"trace_id\":\"4bf92f3577b34da6a3ce929d0e0e4736\"");
    }

    @Test
    void customAttributesCannotOverwriteTelemetryContract() {
        StructuredLogEvent.Builder builder = StructuredLogEvent.builder(resource())
                .severity(LogSeverity.INFO)
                .eventName("checkout.started")
                .body("Checkout started");

        assertThatThrownBy(() -> builder.attribute("service.name", "attacker-controlled"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> builder.attribute("custom.value", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void untrustedRequestIdWithHeaderInjectionIsReplaced() {
        RequestCorrelation correlation = RequestCorrelation.fromHeaderOrGenerate("trusted\r\nx-admin: true");

        assertThat(correlation.requestId())
                .startsWith("req-")
                .doesNotContain("\r", "\n");
    }

    @Test
    void metricGuardRejectsInvalidAndHighCardinalityLabels() {
        assertThatThrownBy(() -> MetricCardinalityGuard.validateLabelName("request_id"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MetricCardinalityGuard.validateLabelName("bad-label"))
                .isInstanceOf(IllegalArgumentException.class);

        MetricCardinalityGuard.validateLabelValue("status_class", "2xx");
    }

    @Test
    void routeRequiresTemplateInsteadOfConcreteIdentifier() {
        assertThat(RouteTemplate.of("/orders/:id/pay").value()).isEqualTo("/orders/:id/pay");
        assertThatThrownBy(() -> RouteTemplate.of("/orders/123/pay"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RouteTemplate.of("/orders/:id?debug=true"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private ServiceResource resource() {
        return ServiceResource.builder()
                .serviceName("checkout-api")
                .serviceVersion("1.0.0")
                .deploymentEnvironmentName("test")
                .serviceInstanceId("instance-1")
                .build();
    }
}
