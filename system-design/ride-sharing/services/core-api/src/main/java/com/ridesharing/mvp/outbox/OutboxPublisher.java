package com.ridesharing.mvp.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Publisher eventów zapisanych w tabeli outbox.
 *
 * W aplikacji ride-sharing Outbox Pattern zabezpiecza krytyczny problem:
 * zmiana stanu w bazie i publikacja eventu do Kafki muszą być ze sobą spójne.
 *
 * Przykład:
 * - RideService zapisuje przejazd jako DRIVER_ASSIGNED,
 * - w tej samej transakcji zapisuje event RideMatched do outbox_events,
 * - OutboxPublisher później publikuje ten event do Kafki.
 *
 * Dzięki temu nie tracimy eventu, nawet jeśli aplikacja padnie zaraz po zapisie do bazy.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.kafka.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class OutboxPublisher {

    /**
     * Repozytorium eventów outbox.
     *
     * Z niego pobieramy eventy w statusie PENDING i aktualizujemy ich status
     * po udanej albo nieudanej publikacji.
     */
    private final OutboxEventRepository outbox;

    /**
     * KafkaTemplate używany do publikowania payloadu eventu do odpowiedniego topicu.
     *
     * Keyem wiadomości jest aggregateId, np. rideId.
     * To ważne, bo Kafka będzie utrzymywać kolejność eventów dla tego samego aggregateId
     * w ramach jednej partycji.
     */
    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * Cyklicznie publikuje oczekujące eventy z outboxa do Kafki.
     *
     * Domyślnie odpala się co 1000 ms, ale opóźnienie można zmienić konfiguracją:
     * app.outbox.poll-delay-ms
     *
     * Flow:
     * 1. Pobiera maksymalnie 50 najstarszych eventów PENDING.
     * 2. Publikuje każdy event do topicu zapisanego w rekordzie.
     * 3. Po sukcesie ustawia status PUBLISHED i publishedAt.
     * 4. Po błędzie zwiększa licznik attempts i zapisuje lastError.
     * 5. Po 10 nieudanych próbach oznacza event jako FAILED.
     *
     * Metoda jest transakcyjna, więc zmiany statusu eventów są zapisywane razem
     * z końcem wykonania tej metody.
     */
    @Scheduled(fixedDelayString = "${app.outbox.poll-delay-ms:1000}")
    @Transactional
    public void publishPending() {
        /*
         * Pobieramy małą paczkę eventów, żeby publisher nie próbował naraz wysłać
         * całej tabeli outbox. To ogranicza obciążenie bazy i Kafki.
         */
        for (var event : outbox.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING)) {
            try {
                /*
                 * Publikacja do Kafki.
                 *
                 * topic pochodzi z rekordu outbox,
                 * key = aggregateId,
                 * value = payload JSON.
                 *
                 * .get() wymusza synchroniczne oczekiwanie na potwierdzenie wysłania.
                 * Dzięki temu status PUBLISHED ustawiamy dopiero wtedy, gdy Kafka potwierdzi zapis.
                 */
                kafkaTemplate
                        .send(
                                event.getTopic(),
                                event.getAggregateId().toString(),
                                event.getPayload()
                        )
                        .get();

                /*
                 * Event został skutecznie opublikowany.
                 * Od tego momentu consumery mogą go przetwarzać z Kafki.
                 */
                event.setStatus(OutboxStatus.PUBLISHED);
                event.setPublishedAt(Instant.now());
                event.setLastError(null);
            } catch (Exception ex) {
                /*
                 * Publikacja się nie udała.
                 * Nie tracimy eventu — zostaje w outboxie i będzie ponawiany.
                 */
                event.setAttempts(event.getAttempts() + 1);
                event.setLastError(ex.getMessage());

                /*
                 * Po 10 próbach uznajemy event za problematyczny.
                 * Trafia do statusu FAILED, żeby nie blokował w nieskończoność kolejki PENDING.
                 */
                if (event.getAttempts() >= 10) {
                    event.setStatus(OutboxStatus.FAILED);
                }

                log.warn(
                        "Outbox publish failed for {}: {}",
                        event.getId(),
                        ex.getMessage()
                );
            }
        }
    }
}