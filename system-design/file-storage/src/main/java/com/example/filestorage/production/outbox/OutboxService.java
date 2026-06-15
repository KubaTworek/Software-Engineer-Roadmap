package com.example.filestorage.production.outbox;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Serwis odpowiedzialny za dodawanie eventów do transactional outboxa.
 *
 * Transactional outbox służy do bezpiecznego zapisywania zdarzeń domenowych
 * razem ze zmianą danych w bazie.
 *
 * Przykład:
 * - użytkownik uploaduje plik,
 * - aplikacja zapisuje FileMetadata,
 * - w tej samej transakcji zapisuje event FILE_CREATED do tabeli outbox,
 * - później OutboxPublisherWorker publikuje ten event dalej.
 *
 * Dzięki temu nie ma sytuacji, w której dane w bazie zostały zapisane,
 * ale event nie został utworzony przez awarię aplikacji między DB a brokerem.
 */
@Service
public class OutboxService {

    /**
     * Repozytorium eventów outboxa.
     *
     * Odpowiada za zapis OutboxEvent do bazy.
     * Worker publikujący będzie później pobierał eventy PENDING z tego repozytorium.
     */
    private final OutboxEventRepository repository;

    public OutboxService(OutboxEventRepository repository) {
        this.repository = repository;
    }

    /**
     * Dodaje nowy event do outboxa.
     *
     * Parametry:
     * - eventType: typ zdarzenia, np. FILE_CREATED, FILE_DELETED, FILE_SHARED,
     * - aggregateType: typ agregatu, np. FILE, FOLDER, USER,
     * - aggregateId: identyfikator agregatu, którego dotyczy zdarzenie,
     * - payload: dane zdarzenia, zwykle JSON jako String.
     *
     * Metoda jest transakcyjna, żeby event mógł być zapisany razem
     * z operacją domenową, która go wywołała.
     *
     * Jeżeli metoda zostanie wywołana wewnątrz istniejącej transakcji,
     * dołączy do niej. To jest oczekiwane zachowanie dla transactional outbox.
     */
    @Transactional
    public OutboxEvent enqueue(String eventType,
                               String aggregateType,
                               UUID aggregateId,
                               String payload) {
        /*
         * Tworzymy event w statusie początkowym, zwykle PENDING.
         * Szczegóły takie jak status, attempts i createdAt powinny być ustawiane
         * w konstruktorze OutboxEvent.
         */
        return repository.save(
                new OutboxEvent(
                        eventType,
                        aggregateType,
                        aggregateId,
                        payload
                )
        );
    }
}