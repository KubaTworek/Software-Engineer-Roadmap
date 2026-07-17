package com.example.ecommerce.inventory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Worker odpowiedzialny za cykliczne wygaszanie przeterminowanych rezerwacji inventory.
 *
 * W checkoutcie stock jest najpierw rezerwowany, a dopiero później potwierdzany
 * po udanej płatności.
 *
 * Problem:
 * jeśli użytkownik nie opłaci zamówienia, zamknie przeglądarkę albo płatność utknie,
 * zarezerwowany stock nie powinien być zablokowany na zawsze.
 *
 * Ten worker regularnie znajduje wygasłe rezerwacje i zwalnia stock z powrotem
 * do puli dostępnych produktów.
 */
@Component
@ConditionalOnProperty(
        prefix = "app.inventory.expiration-worker",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class InventoryExpirationWorker {

    /**
     * Logger techniczny.
     *
     * Logujemy tylko przypadki, gdy faktycznie coś zostało zwolnione.
     * Dzięki temu logi pokazują realne zdarzenia biznesowe, a nie każdy pusty cykl workera.
     */
    private static final Logger log = LoggerFactory.getLogger(InventoryExpirationWorker.class);

    /**
     * Serwis inventory.
     *
     * Worker nie implementuje sam logiki magazynowej.
     * Deleguje ją do InventoryService, który wie:
     * - które rezerwacje wygasły,
     * - jak zwolnić reservedQuantity,
     * - jak oznaczyć rezerwację jako RELEASED,
     * - jakie eventy domenowe zapisać.
     */
    private final InventoryService inventory;

    /**
     * Maksymalna liczba rezerwacji przetwarzanych w jednym cyklu.
     *
     * Batch chroni aplikację przed sytuacją, w której worker próbuje naraz
     * przetworzyć tysiące rekordów i blokuje bazę albo zużywa zbyt dużo zasobów.
     */
    private final int batchSize;

    /**
     * Constructor injection.
     *
     * batchSize jest pobierany z konfiguracji:
     *
     * app.inventory.expiration-worker.batch-size=100
     *
     * Jeśli konfiguracja nie istnieje, domyślnie używane jest 100.
     */
    public InventoryExpirationWorker(
            InventoryService inventory,
            @Value("${app.inventory.expiration-worker.batch-size:100}") int batchSize
    ) {
        this.inventory = inventory;
        this.batchSize = batchSize;
    }

    /**
     * Cyklicznie wygasza stare rezerwacje inventory.
     *
     * Harmonogram:
     *
     * app.inventory.expiration-worker.fixed-delay-ms=30000
     *
     * Domyślnie worker uruchamia się co 30 sekund po zakończeniu poprzedniego cyklu.
     *
     * fixedDelay oznacza, że jeśli przetwarzanie trwa dłużej, kolejny cykl
     * nie wystartuje równolegle w tej samej instancji aplikacji.
     */
    @Scheduled(fixedDelayString = "${app.inventory.expiration-worker.fixed-delay-ms:30000}")
    public void expireReservations() {
        /*
         * InventoryService wykonuje właściwą pracę:
         * - znajduje rezerwacje po expiresAt,
         * - zwalnia stock,
         * - zmienia status rezerwacji,
         * - zapisuje eventy/outbox,
         * - zwraca liczbę przetworzonych rekordów.
         */
        int expired = inventory.expireReservations(batchSize);

        /*
         * Logujemy tylko wtedy, gdy faktycznie wygasły jakieś rezerwacje.
         *
         * To ważne operacyjnie:
         * większa liczba wygasłych rezerwacji może oznaczać np. problem z płatnościami,
         * porzucone checkouty albo nieudane finalizacje zamówień.
         */
        if (expired > 0) {
            log.info("Expired inventory reservations count={}", expired);
        }
    }
}