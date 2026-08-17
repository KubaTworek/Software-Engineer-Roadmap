package com.example.ecommerce.integration.erp;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Worker odpowiedzialny za asynchroniczną synchronizację danych z systemem ERP.
 *
 * ERP w e-commerce zwykle odpowiada za obszary takie jak:
 * - księgowość,
 * - faktury,
 * - rozliczenia,
 * - kartoteki produktów,
 * - stany finansowe,
 * - raportowanie sprzedaży,
 * - integracje z systemami back-office.
 *
 * Ta klasa nie wykonuje synchronizacji bezpośrednio w momencie operacji biznesowej.
 * Zamiast tego cyklicznie pobiera zapisane joby z tabeli erp_sync_jobs
 * i wysyła je przez ErpClient.
 *
 * Dzięki temu checkout, faktura czy zwrot nie muszą czekać na ERP.
 */
@Component
@ConditionalOnProperty(
        prefix = "app.stage4.erp",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ErpSyncWorker {

    /**
     * Repozytorium jobów synchronizacji ERP.
     *
     * Job reprezentuje jedną rzecz do wysłania do ERP, np.:
     * - nowe zamówienie,
     * - wystawioną fakturę,
     * - zwrot,
     * - aktualizację produktu,
     * - zmianę stocku.
     */
    private final ErpSyncJobRepository jobs;

    /**
     * Klient ERP.
     *
     * Odpowiada za faktyczne wysłanie danych do ERP.
     * W obecnej wersji może być mockiem, ale worker jest już przygotowany
     * na prawdziwe wywołania integracyjne.
     */
    private final ErpClient client;

    /**
     * Constructor injection.
     *
     * Worker potrzebuje tylko repozytorium jobów oraz klienta ERP.
     */
    public ErpSyncWorker(
            ErpSyncJobRepository jobs,
            ErpClient client
    ) {
        this.jobs = jobs;
        this.client = client;
    }

    /**
     * Cyklicznie synchronizuje nowe joby z ERP.
     *
     * @Scheduled(fixedDelay = 10000):
     * metoda uruchamia się co 10 sekund po zakończeniu poprzedniego cyklu.
     *
     * fixedDelay jest bezpieczne, bo jeśli jeden cykl trwa dłużej,
     * kolejny nie wystartuje równolegle w tej samej instancji aplikacji.
     *
     * @Transactional:
     * zmiany statusów jobów, np. SENT albo FAILED, są zapisywane w jednej transakcji.
     */
    @Scheduled(fixedDelay = 10000)
    @Transactional
    public void sync() {
        /*
         * Pobieramy maksymalnie 100 najstarszych jobów w statusie NEW.
         *
         * Limit chroni aplikację przed próbą przetworzenia zbyt dużej liczby
         * rekordów w jednym cyklu.
         *
         * Sortowanie po ID daje prostą kolejność FIFO:
         * najstarsze joby są wysyłane jako pierwsze.
         */
        for (ErpSyncJob job : jobs.findTop100ByStatusOrderByIdAsc(ErpSyncStatus.NEW)) {
            try {
                /*
                 * Wysyłamy job do ERP.
                 *
                 * ErpClient powinien obsługiwać szczegóły integracji:
                 * - URL,
                 * - autoryzację,
                 * - retry,
                 * - format payloadu,
                 * - mapowanie błędów.
                 */
                client.send(job);

                /*
                 * Jeśli wysyłka się udała, oznaczamy job jako SENT.
                 *
                 * Dzięki temu ten sam job nie zostanie ponownie wysłany
                 * w kolejnym cyklu workera.
                 */
                job.markSent();
            } catch (Exception e) {
                /*
                 * Jeśli ERP zwróci błąd albo klient rzuci wyjątek,
                 * oznaczamy job jako FAILED.
                 *
                 * Zapisujemy komunikat błędu, żeby można było później
                 * diagnozować problem albo zbudować panel retry/reprocess.
                 */
                job.markFailed(e.getMessage());
            }
        }
    }
}