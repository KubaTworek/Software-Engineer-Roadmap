package com.example.observability.server.cardinality;

import com.example.observability.server.model.MetricIngestRequest;
import com.example.observability.server.model.MetricSeriesDto;
import com.example.observability.server.repository.TelemetryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Guard chroniący system metryk przed eksplozją kardynalności.
 *
 * Kardynalność oznacza liczbę unikalnych time series.
 *
 * Przykład:
 * http_requests_total{service="api", status="200"}
 * http_requests_total{service="api", status="500"}
 *
 * To są dwie różne serie.
 *
 * Problem zaczyna się, gdy ktoś doda label typu:
 * - user_id,
 * - request_id,
 * - session_id,
 * - order_id.
 *
 * Wtedy jedna metryka może wygenerować setki tysięcy albo miliony serii,
 * co bardzo szybko podnosi koszt storage'u i query.
 *
 * Ta klasa działa na ścieżce ingestu metryk, zanim payload trafi do Kafki/storage.
 */
@Service
public class CardinalityGuard {

    /**
     * Konfiguracja polityki kardynalności.
     *
     * Przykładowe limity:
     * - maksymalna liczba labeli na serię,
     * - maksymalna długość wartości labela,
     * - lista zablokowanych label keys,
     * - czy blokować high-risk labels.
     */
    private final CardinalityProperties properties;

    /**
     * Repozytorium używane do zapisania informacji o zaakceptowanych seriach.
     *
     * Dzięki temu można później zbudować raport kardynalności:
     * - ile unikalnych serii ma dana metryka,
     * - które labele mają najwięcej unikalnych wartości,
     * - jaki jest poziom ryzyka kosztowego.
     */
    private final TelemetryRepository repository;

    public CardinalityGuard(
            CardinalityProperties properties,
            TelemetryRepository repository
    ) {
        this.properties = properties;
        this.repository = repository;
    }

    /**
     * Waliduje payload metryk i rejestruje zaakceptowane serie.
     *
     * Ta metoda jest wywoływana w IngestController przed wysłaniem metryk do Kafki.
     *
     * Przepływ:
     * 1. Jeśli request nie ma serii, akceptuje 0 serii.
     * 2. Dla każdej serii sprawdza liczbę labeli.
     * 3. Dla każdego labela sprawdza długość wartości.
     * 4. Opcjonalnie blokuje label keys uznane za ryzykowne.
     * 5. Jeśli wszystko przejdzie, zapisuje informacje o kardynalności.
     *
     * Ważne:
     * jeżeli jedna seria naruszy politykę, odrzucany jest cały payload.
     */
    public CardinalityDecision validateAndRecord(MetricIngestRequest request) {
        if (request.getSeries() == null) {
            return CardinalityDecision.accepted(0);
        }

        int accepted = 0;

        for (MetricSeriesDto series : request.getSeries()) {
            Map<String, String> labels = series.getLabels() == null
                    ? Map.of()
                    : series.getLabels();

            /*
             * Limit liczby labeli na serię.
             *
             * Zbyt wiele labeli oznacza:
             * - większy koszt storage,
             * - większy koszt indeksowania,
             * - większą szansę na eksplozję liczby kombinacji.
             */
            if (labels.size() > properties.getMaxLabelsPerSeries()) {
                throw rejected("too many labels for metric " + series.getName());
            }

            for (Map.Entry<String, String> e : labels.entrySet()) {
                /*
                 * Klucz labela normalizujemy do lowercase.
                 *
                 * Dzięki temu user_id, User_ID i USER_ID są traktowane
                 * jako ten sam typ ryzykownego labela.
                 */
                String key = e.getKey() == null
                        ? ""
                        : e.getKey().toLowerCase(Locale.ROOT);

                String value = e.getValue() == null
                        ? ""
                        : e.getValue();

                /*
                 * Limit długości wartości labela.
                 *
                 * Bardzo długie wartości często oznaczają:
                 * - request id,
                 * - token,
                 * - payload,
                 * - błąd instrumentacji.
                 *
                 * Takie wartości są drogie i zwykle nie nadają się na labels.
                 */
                if (value.length() > properties.getMaxLabelValueLength()) {
                    throw rejected("label value too long: " + key);
                }

                /*
                 * Blokada labeli znanych jako high-cardinality.
                 *
                 * Przykłady:
                 * - user_id,
                 * - request_id,
                 * - session_id,
                 * - trace_id.
                 *
                 * Jeżeli polityka jest włączona, payload z takim labelem
                 * zostaje odrzucony już na ingest.
                 */
                if (
                        properties.isRejectHighRiskLabels()
                                && properties.getBlockedLabelKeys().contains(key)
                ) {
                    throw rejected("blocked high-cardinality label: " + key);
                }
            }

            accepted++;
        }

        /*
         * Zapisujemy informacje o zaakceptowanych seriach w buckecie godzinowym.
         *
         * To nie jest zapis samych metryk.
         * To zapis metadanych potrzebnych do raportowania kardynalności.
         */
        repository.recordMetricCardinality(
                request,
                Instant.now().truncatedTo(ChronoUnit.HOURS)
        );

        return CardinalityDecision.accepted(accepted);
    }

    /**
     * Zwraca raport kardynalności dla konkretnej metryki.
     *
     * Używane przez Phase3Controller:
     * GET /api/v1/phase3/cardinality/report
     *
     * Raport pokazuje:
     * - liczbę unikalnych serii,
     * - najdroższe labele,
     * - przykładowe wartości,
     * - poziom ryzyka.
     */
    public CardinalityReport report(
            String tenantId,
            String metricName,
            int hours
    ) {
        return repository.cardinalityReport(tenantId, metricName, hours);
    }

    /**
     * Buduje wyjątek HTTP 422.
     *
     * 422 Unprocessable Entity oznacza:
     * request jest poprawny składniowo,
     * ale narusza politykę kardynalności.
     *
     * To lepsze niż 500, bo problem jest po stronie payloadu klienta,
     * nie po stronie backendu.
     */
    private ResponseStatusException rejected(String reason) {
        return new ResponseStatusException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "cardinality policy rejected metric payload: " + reason
        );
    }

    /**
     * Raport kardynalności metryki.
     *
     * uniqueSeries mówi, ile unikalnych kombinacji labeli
     * wystąpiło dla danej metryki w analizowanym oknie.
     */
    public record CardinalityReport(
            String tenantId,
            String metricName,
            int lookbackHours,
            long uniqueSeries,
            List<LabelStats> labels
    ) {
    }

    /**
     * Statystyki pojedynczego labela.
     *
     * estimatedUniqueValues:
     * - ile unikalnych wartości miał dany label.
     *
     * risk:
     * - low,
     * - medium,
     * - high.
     *
     * To pomaga szybko znaleźć label, który generuje koszt.
     */
    public record LabelStats(
            String labelKey,
            long estimatedUniqueValues,
            List<String> examples,
            String risk
    ) {
    }
}