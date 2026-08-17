package com.example.videostreaming.personalization;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler cyklicznie przeliczający dane personalizacji.
 *
 * Główna odpowiedzialność:
 * - okresowo uruchamia FeatureStoreService.recompute(),
 * - odświeża lokalny warehouse, feature store i kandydatów rekomendacji,
 * - zapisuje metrykę liczby udanych przeliczeń,
 * - loguje sukces albo błąd przeliczenia.
 *
 * Dzięki temu rekomendacje, trending i ranking nie bazują stale
 * na starych danych z eventów.
 *
 * Ważne:
 * To jest prosty scheduler MVP działający wewnątrz aplikacji.
 * Produkcyjnie ciężkie przeliczenia lepiej wynieść do osobnego job runnera,
 * workflow engine albo batch pipeline'u.
 */
@Component
@ConditionalOnProperty(
        prefix = "app.personalization",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class PersonalizationScheduler {

    private static final Logger log = LoggerFactory.getLogger(PersonalizationScheduler.class);

    /**
     * Serwis feature store.
     *
     * Wykonuje właściwe przeliczenie:
     * - warehouse_daily_video_metrics,
     * - feature_store_user,
     * - feature_store_video,
     * - recommendation_candidates.
     */
    private final FeatureStoreService featureStore;

    /**
     * Licznik udanych przeliczeń personalizacji.
     *
     * Metryka trafia do Micrometer/Prometheus.
     * Pozwala sprawdzić, czy scheduler realnie działa w czasie.
     */
    private final Counter recomputeCounter;

    public PersonalizationScheduler(FeatureStoreService featureStore,
                                    MeterRegistry registry) {
        this.featureStore = featureStore;

        this.recomputeCounter = Counter.builder("personalization_recompute_total")
                .register(registry);
    }

    /**
     * Cyklicznie uruchamia przeliczenie personalizacji.
     *
     * Konfiguracja:
     * - app.personalization.initial-delay-ms:
     *   opóźnienie pierwszego uruchomienia po starcie aplikacji,
     * - app.personalization.recompute-interval-ms:
     *   odstęp między kolejnymi przeliczeniami.
     *
     * Domyślnie:
     * - pierwszy start po 60 sekundach,
     * - kolejne przeliczenia co 10 minut.
     *
     * Flow:
     * 1. Scheduler odpala metodę według fixedDelay.
     * 2. FeatureStoreService przelicza dane.
     * 3. Po sukcesie zwiększamy licznik recompute.
     * 4. Logujemy wynik.
     * 5. W razie błędu logujemy ostrzeżenie i nie wywracamy aplikacji.
     *
     * fixedDelay oznacza, że kolejny start nastąpi dopiero po zakończeniu
     * poprzedniego przeliczenia i odczekaniu wskazanego interwału.
     */
    @Scheduled(
            fixedDelayString = "${app.personalization.recompute-interval-ms:600000}",
            initialDelayString = "${app.personalization.initial-delay-ms:60000}"
    )
    public void scheduledRecompute() {
        try {
            /*
             * To może być ciężka operacja SQL.
             *
             * W MVP działa w procesie aplikacji.
             * Przy większej skali lepiej przenieść ją do osobnego workera,
             * żeby nie obciążała instancji obsługujących requesty użytkowników.
             */
            var result = featureStore.recompute();

            recomputeCounter.increment();

            log.info("Personalization recompute completed: {}", result);
        } catch (Exception e) {
            /*
             * Nie propagujemy wyjątku.
             *
             * Scheduler powinien spróbować ponownie przy następnym cyklu,
             * zamiast zatrzymać cały mechanizm przez pojedynczy błąd.
             */
            log.warn("Personalization recompute failed", e);
        }
    }
}