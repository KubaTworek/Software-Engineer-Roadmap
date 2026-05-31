package pl.jakubtworek.booking.service.async;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.booking.entity.AuditLog;
import pl.jakubtworek.booking.repository.AuditLogRepository;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Serwis odpowiedzialny za asynchroniczny zapis audytu.
 *
 * Audit log jest przykładem side-effectu po potwierdzeniu rezerwacji.
 *
 * W flow asynchronicznym nie chcemy, żeby zapis audytu blokował główną operację
 * potwierdzania rezerwacji dłużej niż to konieczne.
 *
 * Ten serwis pokazuje:
 * - CompletableFuture,
 * - uruchamianie zadania na własnym executorze,
 * - zapis do bazy w tle,
 * - fallback w przypadku błędu side-effectu.
 */
@Service
public class AuditLogService {

    /**
     * Repozytorium JPA dla wpisów audytowych.
     *
     * Używane do zapisania rekordu AuditLog w tabeli audit_logs.
     */
    private final AuditLogRepository auditLogRepository;

    /**
     * Executor używany do uruchamiania operacji asynchronicznych.
     *
     * Wstrzykujemy konkretny ThreadPoolExecutor bookingAsyncExecutor,
     * żeby nie używać przypadkowo ForkJoinPool.commonPool().
     *
     * Dzięki temu mamy kontrolę nad:
     * - liczbą wątków,
     * - kolejką,
     * - nazwami wątków,
     * - shutdownem puli.
     */
    private final ThreadPoolExecutor executor;

    /**
     * Constructor injection.
     *
     * Nazwa parametru bookingAsyncExecutor powinna odpowiadać nazwie beana
     * z AsyncExecutorConfig.
     */
    public AuditLogService(AuditLogRepository auditLogRepository,
                           ThreadPoolExecutor bookingAsyncExecutor) {
        this.auditLogRepository = auditLogRepository;
        this.executor = bookingAsyncExecutor;
    }

    /**
     * Uruchamia asynchroniczny zapis audytu dla potwierdzonej rezerwacji.
     *
     * CompletableFuture.supplyAsync(...) wykonuje zadanie na przekazanym executorze.
     *
     * Zwracamy CompletableFuture<SideEffectResult>, żeby wywołujący mógł:
     * - poczekać na wynik,
     * - połączyć go z innymi side-effectami przez allOf/thenCombine,
     * - sprawdzić, czy audyt się udał.
     */
    public CompletableFuture<SideEffectResult> writeReservationConfirmed(UUID reservationId) {
        return CompletableFuture
                .supplyAsync(() -> writeAuditLog(reservationId), executor)

                /*
                 * Jeśli zapis audytu rzuci wyjątek, nie propagujemy go jako błędu
                 * całego flow.
                 *
                 * Zamiast tego zwracamy SideEffectResult.failure(...).
                 *
                 * To jest świadoma decyzja:
                 * błąd audytu nie powinien automatycznie cofać potwierdzenia
                 * rezerwacji, ale powinien być widoczny diagnostycznie.
                 */
                .exceptionally(error -> SideEffectResult.failure("audit", error));
    }

    /**
     * Zapisuje wpis audytowy w bazie.
     *
     * @Transactional sugeruje, że zapis powinien odbywać się w transakcji.
     *
     * Uwaga o Spring proxy:
     * jeśli ta metoda jest wywołana z tej samej klasy przez this, adnotacja
     * @Transactional może zostać pominięta, bo wywołanie nie przechodzi przez proxy.
     *
     * W tej klasie writeReservationConfirmed(...) wywołuje writeAuditLog(...)
     * bezpośrednio w lambdzie, więc warto mieć świadomość tej pułapki.
     */
    @Transactional
    public SideEffectResult writeAuditLog(UUID reservationId) {
        /*
         * Tworzymy prosty wpis audytowy.
         *
         * Nie trzymamy tutaj relacji JPA do Reservation, tylko samo reservationId.
         * Dzięki temu audyt jest luźniej powiązany z modelem domenowym.
         */
        auditLogRepository.save(new AuditLog(
                reservationId,
                "RESERVATION_CONFIRMED",
                "Reservation was confirmed asynchronously"
        ));

        return SideEffectResult.success("audit");
    }
}