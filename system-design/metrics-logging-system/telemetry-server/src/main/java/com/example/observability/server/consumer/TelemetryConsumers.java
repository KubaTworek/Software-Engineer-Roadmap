package com.example.observability.server.consumer;

import com.example.observability.server.bloom.LogBloomFilterService;
import com.example.observability.server.fulltext.FullTextIndexService;
import com.example.observability.server.model.LogIngestRequest;
import com.example.observability.server.model.MetricIngestRequest;
import com.example.observability.server.repository.TelemetryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumery Kafki dla danych telemetrycznych.
 *
 * IngestController nie zapisuje logów i metryk bezpośrednio do ClickHouse.
 * Zamiast tego publikuje payloady do Kafki.
 *
 * Ta klasa jest drugą stroną pipeline'u:
 * - czyta logi z topicu logów,
 * - czyta metryki z topicu metryk,
 * - zapisuje dane do repository,
 * - buduje indeksy pomocnicze dla logów.
 *
 * Dzięki temu API ingestu jest odseparowane od storage'u.
 * Jeśli ClickHouse chwilowo zwolni, Kafka może buforować dane.
 */
@Component
public class TelemetryConsumers {

    private static final Logger log = LoggerFactory.getLogger(TelemetryConsumers.class);

    /**
     * Mapper do deserializacji JSON payloadów z Kafki.
     *
     * IngestController serializuje request do JSON stringa,
     * a consumer odtwarza z niego DTO:
     * - LogIngestRequest,
     * - MetricIngestRequest.
     */
    private final ObjectMapper objectMapper;

    /**
     * Repozytorium zapisujące dane do storage'u.
     *
     * Dla logów zapisuje do tabeli logs.
     * Dla metryk zapisuje do tabeli metrics_samples.
     */
    private final TelemetryRepository repository;

    /**
     * Serwis budujący bloom filtery dla logów.
     *
     * Bloom filtery są używane później przez QueryPlanner,
     * żeby pominąć kosztowny scan logów, jeśli szukany token
     * na pewno nie występuje w danym zakresie.
     */
    private final LogBloomFilterService bloomFilterService;

    /**
     * Serwis budujący opcjonalny full-text index dla logów.
     *
     * Indeksuje tokeny z message i zapisuje agregaty,
     * które później mogą pomóc w planowaniu/ograniczaniu wyszukiwania.
     */
    private final FullTextIndexService fullTextIndexService;

    public TelemetryConsumers(
            ObjectMapper objectMapper,
            TelemetryRepository repository,
            LogBloomFilterService bloomFilterService,
            FullTextIndexService fullTextIndexService
    ) {
        this.objectMapper = objectMapper;
        this.repository = repository;
        this.bloomFilterService = bloomFilterService;
        this.fullTextIndexService = fullTextIndexService;
    }

    /**
     * Consumer topicu logów.
     *
     * Topic:
     * telemetry.kafka.logs-topic
     *
     * Consumer group:
     * telemetry-log-writer
     *
     * Przepływ:
     * 1. Odbiera payload JSON z Kafki.
     * 2. Deserializuje go do LogIngestRequest.
     * 3. Ignoruje pusty batch.
     * 4. Zapisuje logi do głównej tabeli logs.
     * 5. Buduje bloom filtery dla batcha.
     * 6. Aktualizuje full-text index.
     *
     * Ważne:
     * zapis logów i budowa indeksów dzieją się w jednym przebiegu consumera.
     * Jeśli indeksowanie rzuci wyjątek po insertLogs(), message może zostać
     * przetworzony ponownie i potencjalnie zdublować logi, jeśli storage
     * nie ma deduplikacji.
     */
    @KafkaListener(
            topics = "${telemetry.kafka.logs-topic}",
            groupId = "telemetry-log-writer"
    )
    public void consumeLogs(String payload) throws Exception {
        LogIngestRequest request = objectMapper.readValue(
                payload,
                LogIngestRequest.class
        );

        /*
         * Pusty batch nie powinien generować zapisu do bazy ani indeksów.
         */
        if (request.getLogs() == null || request.getLogs().isEmpty()) {
            return;
        }

        /*
         * Główny zapis logów.
         *
         * To jest źródło prawdy dla query logów.
         */
        repository.insertLogs(request);

        /*
         * Indeks pomocniczy dla szybkiego odrzucania zapytań contains.
         *
         * Nie zastępuje tabeli logs.
         * Ma tylko pomóc plannerowi zdecydować, czy warto skanować dane.
         */
        bloomFilterService.buildForBatch(
                request.getTenantId(),
                request.getLogs()
        );

        /*
         * Opcjonalny full-text index.
         *
         * W tej architekturze to lekki indeks tokenów, a nie pełny Elasticsearch.
         */
        fullTextIndexService.indexBatch(
                request.getTenantId(),
                request.getLogs()
        );

        log.debug(
                "Inserted {} logs, bloom filters and full-text terms for tenant {}",
                request.getLogs().size(),
                request.getTenantId()
        );
    }

    /**
     * Consumer topicu metryk.
     *
     * Topic:
     * telemetry.kafka.metrics-topic
     *
     * Consumer group:
     * telemetry-metric-writer
     *
     * Przepływ:
     * 1. Odbiera payload JSON z Kafki.
     * 2. Deserializuje go do MetricIngestRequest.
     * 3. Ignoruje pusty batch.
     * 4. Zapisuje próbki metryk do metrics_samples.
     *
     * Kontrola quota i kardynalności dzieje się wcześniej,
     * w IngestController/CardinalityGuard.
     * Consumer zakłada, że payload przeszedł walidację na wejściu.
     */
    @KafkaListener(
            topics = "${telemetry.kafka.metrics-topic}",
            groupId = "telemetry-metric-writer"
    )
    public void consumeMetrics(String payload) throws Exception {
        MetricIngestRequest request = objectMapper.readValue(
                payload,
                MetricIngestRequest.class
        );

        if (request.getSeries() == null || request.getSeries().isEmpty()) {
            return;
        }

        /*
         * Zapis raw samples.
         *
         * Rollupy/downsampling powinny być robione przez osobny job,
         * nie bezpośrednio w consumerze.
         */
        repository.insertMetrics(request);

        log.debug("Inserted metrics for tenant {}", request.getTenantId());
    }
}