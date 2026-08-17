package com.example.ecommerce.integration.erp;

import com.example.ecommerce.integration.IntegrationRetryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Klient integracji z systemem ERP.
 *
 * ERP to zewnętrzny system back-office, do którego e-commerce może wysyłać np.:
 * - zamówienia,
 * - faktury,
 * - zwroty,
 * - korekty,
 * - aktualizacje produktów,
 * - dane rozliczeniowe.
 *
 * Ta klasa jest adapterem technicznym.
 * Ukrywa przed resztą aplikacji sposób komunikacji z ERP.
 *
 * W tej wersji projektu klient jest mockiem:
 * - nie wykonuje realnego HTTP requestu,
 * - loguje payload,
 * - używa retry tak, jak używałby go prawdziwy klient ERP.
 */
@Component
public class ErpClient {

    /**
     * Logger techniczny.
     *
     * W obecnej wersji zastępuje realne wysłanie danych do ERP.
     * Dzięki temu podczas działania aplikacji widać, jaki payload zostałby wysłany.
     */
    private static final Logger log = LoggerFactory.getLogger(ErpClient.class);

    /**
     * Wspólny mechanizm retry dla integracji zewnętrznych.
     *
     * ERP może być chwilowo niedostępny, zwrócić timeout albo błąd 5xx.
     * Retry chroni aplikację przed jednorazowymi problemami technicznymi.
     */
    private final IntegrationRetryService retry;

    /**
     * Constructor injection.
     *
     * Klient potrzebuje tylko retry service.
     * W produkcyjnej wersji doszłyby tutaj np.:
     * - WebClient albo RestClient,
     * - konfiguracja baseUrl,
     * - credentials/API key,
     * - mapper payloadów,
     * - obsługa kodów odpowiedzi ERP.
     */
    public ErpClient(IntegrationRetryService retry) {
        this.retry = retry;
    }

    /**
     * Wysyła pojedynczy job synchronizacji do ERP.
     *
     * Parametr job zawiera pełny kontekst operacji:
     * - entityType — typ obiektu, np. Order, Invoice, ReturnRequest,
     * - entityId — ID obiektu w systemie e-commerce,
     * - operation — rodzaj operacji, np. CREATE, UPDATE, REFUND,
     * - payloadJson — snapshot danych do wysłania.
     *
     * Flow:
     * 1. ErpSyncWorker pobiera job w statusie NEW.
     * 2. Wywołuje ErpClient.send(job).
     * 3. ErpClient wykonuje wysyłkę z retry.
     * 4. Jeśli się uda, worker oznacza job jako SENT.
     * 5. Jeśli się nie uda, worker oznacza job jako FAILED.
     *
     * W tej wersji zamiast realnej wysyłki logujemy dane.
     */
    public void send(ErpSyncJob job) {
        retry.run(
                "erp.sync",
                () -> log.info(
                        "MOCK_ERP_SYNC entityType={}, entityId={}, operation={}, payload={}",
                        job.getEntityType(),
                        job.getEntityId(),
                        job.getOperation(),
                        job.getPayloadJson()
                )
        );
    }
}