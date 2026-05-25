package pl.jakubtworek.backend_engineering.stage_3.block_b.prometheus;

/**
 * Provides a Prometheus recording rules template.
 *
 * Recording rules should precompute expensive or frequently reused queries.
 * Ratios should aggregate numerators and denominators separately before division.
 */
public final class RecordingRulesTemplate {

    private RecordingRulesTemplate() {
    }

    public static final String CHECKOUT_API_RECORDING_RULES = """
            groups:
            - name: checkout-api-recording
              interval: 30s
              rules:
              - record: route:checkout_http_requests:rate5m
                expr: |
                  sum without (instance, pod) (
                    rate(checkout_http_requests_total{service="checkout-api"}[5m])
                  )

              - record: route:checkout_http_5xx_per_requests:ratio_rate5m
                expr: |
                  sum without (instance, pod) (
                    rate(checkout_http_requests_total{service="checkout-api",status_code=~"5.."}[5m])
                  )
                  /
                  sum without (instance, pod) (
                    rate(checkout_http_requests_total{service="checkout-api"}[5m])
                  )

              - record: route:checkout_http_request_duration_seconds:p95_rate5m
                expr: |
                  histogram_quantile(
                    0.95,
                    sum by (route, le) (
                      rate(checkout_http_request_duration_seconds_bucket{service="checkout-api"}[5m])
                    )
                  )

              - record: route:checkout_http_request_duration_seconds:p99_rate5m
                expr: |
                  histogram_quantile(
                    0.99,
                    sum by (route, le) (
                      rate(checkout_http_request_duration_seconds_bucket{service="checkout-api"}[5m])
                    )
                  )
            """;
}