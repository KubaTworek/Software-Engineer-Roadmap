package com.example.observability.server.phase3;

import com.example.observability.server.model.LogQueryResult;
import com.example.observability.server.model.TraceSpanResult;
import com.example.observability.server.repository.TelemetryRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Serwis korelujący różne typy danych telemetrycznych.
 *
 * W systemie observability pojedynczy problem zwykle widać w kilku miejscach:
 * - metryka pokazuje spike, np. wzrost error rate albo latency,
 * - logi pokazują konkretne błędy,
 * - trace'y pokazują, które operacje/requesty były wolne albo nieudane.
 *
 * Ta klasa łączy te źródła danych w jeden wynik diagnostyczny.
 *
 * Nie zapisuje danych.
 * Nie wykonuje detekcji anomalii.
 * Nie odpala alertów.
 *
 * Jej zadaniem jest pobrać powiązane dane z repository i zwrócić je razem.
 */
@Service
public class CorrelationService {

    /**
     * Repozytorium telemetryczne używane do odczytu:
     * - logów,
     * - trace spanów.
     *
     * Metryka jest tutaj używana jako kontekst korelacji,
     * ale ta klasa nie pobiera samych punktów metrycznych.
     */
    private final TelemetryRepository repository;

    public CorrelationService(TelemetryRepository repository) {
        this.repository = repository;
    }

    /**
     * Koreluje spike metryki z logami błędów i trace'ami w tym samym oknie czasu.
     *
     * Typowy scenariusz:
     * użytkownik widzi spike na metryce:
     * - http_requests_errors_total,
     * - request_latency_ms,
     * - payment_failures_total.
     *
     * Następnie chce szybko zobaczyć:
     * - jakie ERROR logi wystąpiły w tym czasie,
     * - jakie trace spans były aktywne dla tego serwisu.
     *
     * Parametry:
     * - tenantId: izolacja danych,
     * - service: serwis, którego dotyczy problem,
     * - metricName: metryka, która pokazała spike,
     * - around: punkt czasu, wokół którego szukamy danych,
     * - windowSeconds: szerokość okna przed i po around.
     *
     * Przykład:
     * around = 12:00:00
     * windowSeconds = 300
     *
     * Zakres:
     * 11:55:00 - 12:05:00
     */
    public MetricLogCorrelation correlateMetricSpikeWithLogs(
            String tenantId,
            String service,
            String metricName,
            Instant around,
            int windowSeconds
    ) {
        /*
         * Budujemy symetryczne okno czasowe wokół wskazanego momentu.
         *
         * To pozwala znaleźć zdarzenia tuż przed spike'em i tuż po nim.
         */
        Instant start = around.minusSeconds(windowSeconds);
        Instant end = around.plusSeconds(windowSeconds);

        /*
         * Pobieramy próbkę logów ERROR dla danego serwisu.
         *
         * Limit 100 jest świadomym ograniczeniem:
         * correlation API ma dać szybki kontekst diagnostyczny,
         * a nie zwrócić całą historię logów.
         */
        List<LogQueryResult> errors = repository.queryLogs(
                tenantId,
                service,
                "ERROR",
                null,
                start,
                end,
                100
        );

        /*
         * Pobieramy trace spans dla danego serwisu w tym samym oknie.
         *
         * To pomaga zidentyfikować requesty/operacje,
         * które działy się w czasie spike'a metryki.
         */
        List<TraceSpanResult> traces = repository.queryTraceSpans(
                tenantId,
                null,
                service,
                start,
                end,
                100
        );

        /*
         * Wynik zawiera zarówno liczniki, jak i przykładowe rekordy.
         *
         * Liczniki są przydatne dla UI,
         * a sampleErrors/sampleSpans dla szybkiego debugowania.
         */
        return new MetricLogCorrelation(
                tenantId,
                service,
                metricName,
                start,
                end,
                errors.size(),
                traces.size(),
                errors,
                traces
        );
    }

    /**
     * Koreluje dane telemetryczne po traceId.
     *
     * To jest najprecyzyjniejsza forma korelacji.
     *
     * traceId reprezentuje pojedynczy request albo przepływ przez system.
     * Jeśli logi i spany mają ten sam traceId, można odtworzyć:
     * - które serwisy brały udział,
     * - jakie operacje zostały wykonane,
     * - które logi powstały w trakcie obsługi requestu,
     * - gdzie wystąpił błąd albo opóźnienie.
     *
     * Parametry:
     * - tenantId: izolacja danych,
     * - traceId: identyfikator trace'a.
     */
    public TraceCorrelation correlateByTraceId(
            String tenantId,
            String traceId
    ) {
        /*
         * Pobieramy logi powiązane bezpośrednio przez trace_id.
         *
         * Limit 500 jest większy niż przy spike correlation,
         * bo pojedynczy trace może mieć wiele logów, ale nadal powinien być
         * ograniczony, żeby API nie zwróciło niekontrolowanie dużego wyniku.
         */
        List<LogQueryResult> logs = repository.queryLogsByTraceId(
                tenantId,
                traceId,
                500
        );

        /*
         * Pobieramy wszystkie spany dla traceId.
         *
         * Brak filtra service oznacza:
         * pokaż cały przepływ requestu przez wszystkie serwisy.
         */
        List<TraceSpanResult> spans = repository.queryTraceSpans(
                tenantId,
                traceId,
                null,
                null,
                null,
                500
        );

        return new TraceCorrelation(
                tenantId,
                traceId,
                spans.size(),
                logs.size(),
                spans,
                logs
        );
    }

    /**
     * Wynik korelacji spike'a metryki z logami i trace'ami.
     *
     * Zawiera:
     * - tenantId,
     * - service,
     * - metricName,
     * - analizowane okno czasu,
     * - liczbę znalezionych ERROR logów,
     * - liczbę znalezionych spanów,
     * - próbkę logów błędów,
     * - próbkę spanów.
     *
     * Ten model jest wygodny dla UI:
     * można pokazać jedną kartę "co działo się wokół spike'a".
     */
    public record MetricLogCorrelation(
            String tenantId,
            String service,
            String metricName,
            Instant start,
            Instant end,
            int errorLogCount,
            int spanCount,
            List<LogQueryResult> sampleErrors,
            List<TraceSpanResult> sampleSpans
    ) {
    }

    /**
     * Wynik korelacji po traceId.
     *
     * Zawiera:
     * - wszystkie znalezione spany trace'a,
     * - logi powiązane z tym samym traceId,
     * - liczniki pomocnicze dla UI.
     *
     * To jest podstawowy model do widoku:
     * "pokaż mi wszystko o tym requestcie".
     */
    public record TraceCorrelation(
            String tenantId,
            String traceId,
            int spanCount,
            int logCount,
            List<TraceSpanResult> spans,
            List<LogQueryResult> logs
    ) {
    }
}