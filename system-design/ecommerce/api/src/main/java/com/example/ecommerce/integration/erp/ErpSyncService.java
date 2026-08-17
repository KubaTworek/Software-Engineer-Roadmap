package com.example.ecommerce.integration.erp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serwis odpowiedzialny za dodawanie zadań synchronizacji z ERP.
 *
 * Ta klasa NIE wysyła danych bezpośrednio do ERP.
 * Jej zadaniem jest zapisanie joba w bazie, żeby ErpSyncWorker mógł go później
 * asynchronicznie przetworzyć.
 *
 * Dzięki temu główne procesy e-commerce, np. checkout, faktura albo zwrot,
 * nie muszą czekać na odpowiedź z zewnętrznego systemu ERP.
 */
@Service
public class ErpSyncService {

    /**
     * Repozytorium jobów ERP.
     *
     * Każdy rekord ErpSyncJob reprezentuje jedną operację do wysłania do ERP,
     * np.:
     * - utworzenie zamówienia,
     * - wystawienie faktury,
     * - zwrot,
     * - aktualizację produktu,
     * - zmianę stocku.
     */
    private final ErpSyncJobRepository jobs;

    /**
     * ObjectMapper używany do zapisania payloadu jako JSON.
     *
     * Payload może być dowolnym obiektem domenowym lub DTO.
     * Przed zapisem do bazy zamieniamy go na String JSON,
     * żeby worker mógł później wysłać dokładnie ten sam snapshot danych.
     */
    private final ObjectMapper objectMapper;

    /**
     * Constructor injection.
     *
     * Serwis potrzebuje repozytorium jobów oraz ObjectMappera
     * do serializacji danych wysyłanych do ERP.
     */
    public ErpSyncService(
            ErpSyncJobRepository jobs,
            ObjectMapper objectMapper
    ) {
        this.jobs = jobs;
        this.objectMapper = objectMapper;
    }

    /**
     * Dodaje nowy job synchronizacji ERP.
     *
     * Parametry:
     * - entityType — typ obiektu biznesowego, np. "Order", "Invoice", "ReturnRequest",
     * - entityId — ID obiektu w systemie e-commerce,
     * - operation — typ operacji, np. "CREATE", "UPDATE", "CANCEL", "REFUND",
     * - payload — dane, które mają zostać wysłane do ERP.
     *
     * Flow:
     * 1. Serializujemy payload do JSON-a.
     * 2. Tworzymy ErpSyncJob.
     * 3. Zapisujemy job w bazie.
     * 4. Worker ERP pobierze go później i wyśle do ERP.
     *
     * @Transactional:
     * Job powinien zapisać się atomowo razem z operacją, która go tworzy.
     * Jeśli metoda enqueue() jest wywołana wewnątrz większej transakcji,
     * zapis joba zostanie zatwierdzony albo wycofany razem z nią.
     */
    @Transactional
    public void enqueue(
            String entityType,
            String entityId,
            String operation,
            Object payload
    ) {
        try {
            /*
             * Serializujemy payload do JSON.
             *
             * To jest snapshot danych w momencie utworzenia joba.
             * Dzięki temu późniejsza zmiana encji w bazie nie zmieni automatycznie
             * treści joba oczekującego na wysyłkę.
             */
            String payloadJson = objectMapper.writeValueAsString(payload);

            /*
             * Zapisujemy job jako NEW.
             *
             * ErpSyncWorker cyklicznie pobiera joby w statusie NEW
             * i próbuje wysłać je do ERP.
             */
            jobs.save(
                    new ErpSyncJob(
                            entityType,
                            entityId,
                            operation,
                            payloadJson
                    )
            );
        } catch (JsonProcessingException e) {
            /*
             * Jeśli payloadu nie da się zserializować, nie tworzymy joba.
             *
             * To błąd techniczny po stronie aplikacji albo źle zbudowanego payloadu.
             * Rzucamy wyjątek runtime, żeby transakcja mogła zostać wycofana.
             */
            throw new IllegalStateException("Could not serialize ERP payload", e);
        }
    }
}