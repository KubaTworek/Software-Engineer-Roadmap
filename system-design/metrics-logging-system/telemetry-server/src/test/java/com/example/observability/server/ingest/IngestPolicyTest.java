package com.example.observability.server.ingest;

import com.example.observability.server.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IngestPolicyTest {
    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
    private final IngestPolicy policy = new IngestPolicy(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void rejectsNonFiniteMetricAndFutureTimestamp() {
        var sample = new MetricSampleDto();
        sample.setTimestamp(NOW.plusSeconds(301));
        sample.setValue(Double.NaN);
        var series = new MetricSeriesDto();
        series.setName("requests_total");
        series.setType("counter");
        series.setSamples(List.of(sample));
        var request = new MetricIngestRequest();
        request.setSeries(List.of(series));

        assertThatThrownBy(() -> policy.validate(request))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void redactsSecretsBeforeTheyCanReachKafka() {
        var event = new LogEventDto();
        event.setMessage("user a@example.com used Bearer abc.def");
        event.setAttributes(Map.of("authorization", "secret", "safe", "value"));
        var request = new LogIngestRequest();
        request.setLogs(List.of(event));

        new SensitiveDataRedactor().redact(request);

        assertThat(event.getMessage()).doesNotContain("a@example.com", "abc.def");
        assertThat(event.getAttributes()).containsEntry("authorization", "[REDACTED]")
                .containsEntry("safe", "value");
    }
}
