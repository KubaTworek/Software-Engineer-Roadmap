package pl.jakubtworek.backend.notification.config;

import com.rabbitmq.client.ConnectionFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;
import com.rabbitmq.client.AMQP;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import java.util.HashMap;
import java.util.stream.Collectors;

/** Dowodzi na prawdziwym brokerze, że negatywny ack bez requeue zachowuje event w DLQ. */
@Tag("infrastructure")
@Testcontainers
class RabbitDeadLetterInfrastructureTest {
    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @Test
    void rejectedMessageCarriesDeathHistoryToDlq() throws Exception {
        var factory = new ConnectionFactory();
        factory.setUri(RABBIT.getAmqpUrl());
        try (var connection = factory.newConnection(); var channel = connection.createChannel()) {
            channel.exchangeDeclare("test.dlx", "direct", true);
            channel.queueDeclare("test.main", true, false, false,
                    java.util.Map.of("x-dead-letter-exchange", "test.dlx", "x-dead-letter-routing-key", "test.dlq"));
            channel.queueDeclare("test.dlq", true, false, false, null);
            channel.queueBind("test.dlq", "test.dlx", "test.dlq");
            channel.basicPublish("", "test.main", null, "poison".getBytes(java.nio.charset.StandardCharsets.UTF_8));

            var delivery = channel.basicGet("test.main", false);
            channel.basicReject(delivery.getEnvelope().getDeliveryTag(), false);
            com.rabbitmq.client.GetResponse dead = null;
            for (int attempt = 0; attempt < 20 && dead == null; attempt++) {
                Thread.sleep(50);
                dead = channel.basicGet("test.dlq", true);
            }
            assertThat(dead).isNotNull();
            assertThat(dead.getProps().getHeaders()).containsKey("x-death");
        }
    }

    @Test
    void traceparentCrossesRabbitMqAndConsumerSpanKeepsTheSameTrace() throws Exception {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        SdkTracerProvider provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter)).build();
        OpenTelemetry telemetry = OpenTelemetrySdk.builder().setTracerProvider(provider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance())).build();
        try {
            Span producer = telemetry.getTracer("project-3-test").spanBuilder("order.outbox.publish").startSpan();
            var headers = new HashMap<String, Object>();
            telemetry.getPropagators().getTextMapPropagator().inject(Context.root().with(producer), headers,
                    (carrier, key, value) -> carrier.put(key, value));

            var factory = new ConnectionFactory();
            factory.setUri(RABBIT.getAmqpUrl());
            try (var connection = factory.newConnection(); var channel = connection.createChannel()) {
                channel.queueDeclare("test.trace", false, false, true, null);
                var properties = new AMQP.BasicProperties.Builder().headers(headers).build();
                channel.basicPublish("", "test.trace", properties, "event".getBytes());
                var delivery = channel.basicGet("test.trace", true);
                assertThat(delivery).isNotNull();
                var received = delivery.getProps().getHeaders();
                Context parent = telemetry.getPropagators().getTextMapPropagator().extract(Context.root(), received,
                        new io.opentelemetry.context.propagation.TextMapGetter<>() {
                            @Override public Iterable<String> keys(java.util.Map<String, Object> carrier) { return carrier.keySet(); }
                            @Override public String get(java.util.Map<String, Object> carrier, String key) {
                                Object value = carrier.get(key);
                                return value == null ? null : value.toString();
                            }
                        });
                Span consumer = telemetry.getTracer("project-3-test").spanBuilder("notification.consume")
                        .setParent(parent).startSpan();
                consumer.end();
            }
            producer.end();

            var spans = exporter.getFinishedSpanItems().stream()
                    .collect(Collectors.toMap(span -> span.getName(), span -> span));
            assertThat(spans.get("notification.consume").getTraceId())
                    .isEqualTo(spans.get("order.outbox.publish").getTraceId());
            assertThat(spans.get("notification.consume").getParentSpanId())
                    .isEqualTo(spans.get("order.outbox.publish").getSpanId());
        } finally {
            provider.close();
        }
    }
}
