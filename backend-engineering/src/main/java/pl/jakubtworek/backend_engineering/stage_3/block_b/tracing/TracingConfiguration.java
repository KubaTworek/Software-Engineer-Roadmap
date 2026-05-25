package pl.jakubtworek.backend_engineering.stage_3.block_b.tracing;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.semconv.ServiceAttributes;

/**
 * Creates the OpenTelemetry SDK setup for the application.
 *
 * In Spring Boot or Kubernetes this configuration is often provided by auto-instrumentation.
 * This class is useful when the service owns its tracing SDK setup explicitly.
 */
public final class TracingConfiguration {

    private TracingConfiguration() {
    }

    public static OpenTelemetry createOpenTelemetry(String otlpEndpoint) {
        Resource resource = Resource.getDefault()
                .toBuilder()
                .put(ServiceAttributes.SERVICE_NAME, TracingConstants.SERVICE_NAME)
                .put(TracingAttributes.DEPLOYMENT_ENVIRONMENT_NAME, TracingConstants.DEPLOYMENT_ENVIRONMENT_NAME)
                .build();

        OtlpGrpcSpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint(otlpEndpoint)
                .build();

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setResource(resource)
                .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
                .build();

        return OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(ContextPropagators.create(
                        io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator.getInstance()
                ))
                .buildAndRegisterGlobal();
    }

    public static Tracer createTracer(OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer("checkout-api");
    }
}