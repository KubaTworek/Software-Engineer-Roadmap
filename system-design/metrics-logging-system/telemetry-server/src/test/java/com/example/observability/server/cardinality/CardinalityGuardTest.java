package com.example.observability.server.cardinality;

import com.example.observability.server.model.MetricIngestRequest;
import com.example.observability.server.model.MetricSeriesDto;
import com.example.observability.server.repository.TelemetryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * W systemie telemetrycznym pojedynczy nieograniczony label może wygenerować
 * miliony serii. Test chroni budżet cardinality przed zapisem do kolejki/storage.
 */
class CardinalityGuardTest {

    private final TelemetryRepository repository = mock(TelemetryRepository.class);
    private final CardinalityBudgetStore budget = mock(CardinalityBudgetStore.class);
    private final CardinalityGuard guard;

    CardinalityGuardTest() {
        when(budget.reserve(any(), any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt())).thenReturn(true);
        guard = new CardinalityGuard(new CardinalityProperties(), repository, budget);
    }

    @Test
    void rejectsIdentityLabelsBeforeRecordingTheSeries() {
        MetricIngestRequest request = requestWithLabels(Map.of("service", "checkout", "user_id", "u-123"));

        assertThatThrownBy(() -> guard.validateAndRecord(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("blocked high-cardinality label");

        verify(repository, never()).recordMetricCardinality(any(), any(Instant.class));
    }

    @Test
    void recordsOnlySeriesThatPassedTheCardinalityPolicy() {
        MetricIngestRequest request = requestWithLabels(Map.of("service", "checkout", "status", "200"));

        CardinalityDecision decision = guard.validateAndRecord(request);

        assertThat(decision.accepted()).isTrue();
        assertThat(decision.acceptedSeries()).isEqualTo(1);
        verify(repository).recordMetricCardinality(any(), any(Instant.class));
    }

    private MetricIngestRequest requestWithLabels(Map<String, String> labels) {
        MetricSeriesDto series = new MetricSeriesDto();
        series.setName("http_requests_total");
        series.setLabels(labels);
        MetricIngestRequest request = new MetricIngestRequest();
        request.setTenantId("tenant-a");
        request.setSeries(List.of(series));
        return request;
    }
}
