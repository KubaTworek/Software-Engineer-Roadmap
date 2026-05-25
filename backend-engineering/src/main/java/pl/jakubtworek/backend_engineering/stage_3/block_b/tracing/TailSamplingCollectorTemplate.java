package pl.jakubtworek.backend_engineering.stage_3.block_b.tracing;

/**
 * Provides an example OpenTelemetry Collector tail-sampling configuration.
 *
 * Tail sampling is not implemented inside the application process.
 * It should usually run in the Collector, where complete traces can be evaluated.
 */
public final class TailSamplingCollectorTemplate {

    private TailSamplingCollectorTemplate() {
    }

    public static final String OTEL_COLLECTOR_TAIL_SAMPLING = """
            receivers:
              otlp:
                protocols:
                  grpc:
                  http:

            processors:
              batch:
              tail_sampling:
                decision_wait: 5s
                num_traces: 50000
                expected_new_traces_per_sec: 2000
                policies:
                  - name: errors
                    type: status_code
                    status_code:
                      status_codes: [ERROR]
                  - name: slow-traces
                    type: latency
                    latency:
                      threshold_ms: 1000
                  - name: baseline
                    type: probabilistic
                    probabilistic:
                      sampling_percentage: 10

            exporters:
              otlp:
                endpoint: tempo-gateway.observability.svc:4317
                tls:
                  insecure: true

            service:
              pipelines:
                traces:
                  receivers: [otlp]
                  processors: [batch, tail_sampling]
                  exporters: [otlp]
            """;
}