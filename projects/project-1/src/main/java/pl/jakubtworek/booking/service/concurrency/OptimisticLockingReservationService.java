package pl.jakubtworek.booking.service.concurrency;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import pl.jakubtworek.booking.dto.ReservationCreateRequest;
import pl.jakubtworek.booking.entity.CapacityPool;
import pl.jakubtworek.booking.entity.Event;
import pl.jakubtworek.booking.exception.CapacityUnavailableException;
import pl.jakubtworek.booking.exception.NotFoundException;
import pl.jakubtworek.booking.repository.CapacityPoolRepository;

import java.util.UUID;

/**
 * Serwis pokazujący strategię optimistic lockingu.
 *
 * To jest jedna ze strategii z Etapu 2 — Concurrency.
 *
 * Optimistic locking zakłada, że konflikty są raczej rzadkie.
 * Zamiast blokować wiersz z góry, aplikacja:
 *
 * 1. odczytuje encję,
 * 2. modyfikuje ją w pamięci,
 * 3. próbuje zapisać,
 * 4. Hibernate sprawdza pole @Version,
 * 5. jeśli ktoś zmienił encję wcześniej, zapis kończy się konfliktem.
 *
 * W tym projekcie optimistic locking działa dzięki polu:
 *
 * @Version
 * private long version;
 *
 * w encji CapacityPool.
 *
 * Jeśli dwie transakcje równocześnie odczytają tę samą pulę miejsc,
 * tylko jedna z nich poprawnie zapisze zmianę. Druga dostanie konflikt
 * optimistic lockingu i musi wykonać retry albo zakończyć się błędem.
 */
@Service
public class OptimisticLockingReservationService {

    /**
     * Maksymalna liczba prób ponowienia operacji po konflikcie optimistic lockingu.
     *
     * Wysoka wartość 200 jest edukacyjna i pomaga testom równoległym pokazać,
     * że przy retry da się finalnie obsłużyć wiele requestów.
     *
     * Produkcyjnie taka wartość może być zbyt wysoka.
     * Przy bardzo gorącym zasobie duża liczba retry może zwiększyć obciążenie
     * aplikacji i bazy.
     */
    private static final int MAX_RETRIES = 200;

    /**
     * Repozytorium puli miejsc.
     *
     * Używamy zwykłego findByEventId(...), bez pesymistycznej blokady.
     * Konflikty wykrywa Hibernate dopiero przy flush/commit dzięki @Version.
     */
    private final CapacityPoolRepository capacityPoolRepository;

    /**
     * Wspólny helper do tworzenia rezerwacji.
     *
     * Dzięki temu różne strategie concurrency nie duplikują logiki:
     * - pobierania eventu,
     * - pobierania/tworzenia klienta,
     * - zapisywania rezerwacji.
     */
    private final ReservationCreationSupport reservationCreationSupport;

    /**
     * TransactionTemplate pozwala programistycznie uruchamiać osobną transakcję
     * dla każdej próby retry.
     *
     * To jest tu ważne:
     * po konflikcie optimistic lockingu bieżąca transakcja jest nieudana i powinna
     * zostać wycofana. Kolejna próba musi działać w świeżej transakcji.
     */
    private final TransactionTemplate transactionTemplate;

    /**
     * Constructor injection.
     *
     * PlatformTransactionManager jest użyty do stworzenia TransactionTemplate.
     */
    public OptimisticLockingReservationService(
            CapacityPoolRepository capacityPoolRepository,
            ReservationCreationSupport reservationCreationSupport,
            PlatformTransactionManager transactionManager
    ) {
        this.capacityPoolRepository = capacityPoolRepository;
        this.reservationCreationSupport = reservationCreationSupport;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Tworzy rezerwację z retry po konflikcie optimistic lockingu.
     *
     * Ta metoda sama nie ma @Transactional.
     *
     * To celowe:
     * każda próba retry ma dostać własną, świeżą transakcję przez TransactionTemplate.
     *
     * Gdyby cała pętla była jedną transakcją, retry po konflikcie byłby problematyczny,
     * bo persistence context mógłby być już w stanie błędu/starego odczytu.
     */
    public UUID create(UUID eventId, ReservationCreateRequest request) {
        OptimisticLockingFailureException lastConflict = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                /*
                 * Każda próba działa w osobnej transakcji.
                 *
                 * Jeśli zapis przejdzie, od razu zwracamy ID rezerwacji.
                 */
                return transactionTemplate.execute(status -> createInTransaction(eventId, request, status));
            } catch (OptimisticLockingFailureException e) {
                /*
                 * Konflikt optimistic lockingu oznacza, że ktoś inny zapisał CapacityPool
                 * między naszym odczytem a naszym flush/commit.
                 *
                 * Nie traktujemy tego jako natychmiastowej awarii.
                 * Robimy retry.
                 */
                lastConflict = e;
                backoff(attempt);
            }
        }

        /*
         * Jeśli po wielu próbach nadal są konflikty, przerywamy operację.
         *
         * To jest ważne, bo nieskończony retry mógłby zablokować request na bardzo długo.
         */
        throw new OptimisticLockingFailureException(
                "Could not reserve capacity after optimistic locking retries",
                lastConflict
        );
    }

    /**
     * Krótki backoff między retry.
     *
     * Bez żadnej przerwy wiele wątków mogłoby ciągle zderzać się o ten sam rekord
     * w bardzo podobnym czasie.
     *
     * Tutaj backoff jest bardzo prosty:
     *
     * - maksymalnie 10 ms,
     * - rośnie lekko wraz z numerem próby.
     *
     * Produkcyjnie zwykle używa się exponential backoff z jitterem.
     */
    private void backoff(int attempt) {
        try {
            Thread.sleep(Math.min(10L, attempt));
        } catch (InterruptedException e) {
            /*
             * Poprawna obsługa InterruptedException:
             * przywracamy flagę przerwania i przerywamy flow wyjątkiem.
             */
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during optimistic locking retry", e);
        }
    }

    /**
     * Jedna próba utworzenia rezerwacji w transakcji.
     *
     * Ta metoda:
     *
     * 1. pobiera event,
     * 2. pobiera CapacityPool,
     * 3. sprawdza dostępność,
     * 4. zmniejsza dostępność,
     * 5. zapisuje rezerwację,
     * 6. wymusza flush, żeby konflikt @Version wyszedł w tej metodzie.
     */
    private UUID createInTransaction(UUID eventId,
                                     ReservationCreateRequest request,
                                     TransactionStatus status) {
        try {
            /*
             * Pobieramy event.
             *
             * To nie jest mechanizm concurrency, tylko sprawdzenie zasobu.
             */
            Event event = reservationCreationSupport.getEvent(eventId);

            /*
             * Pobieramy CapacityPool bez blokady.
             *
             * Jeśli wiele transakcji pobierze tę samą wersję, konflikt zostanie
             * wykryty później przy flush dzięki @Version.
             */
            CapacityPool pool = capacityPoolRepository.findByEventId(eventId)
                    .orElseThrow(() -> new NotFoundException("Capacity pool not found for event: " + eventId));

            /*
             * Sprawdzamy dostępność.
             *
             * Ten check może być oparty o stan, który zaraz stanie się nieaktualny,
             * ale optimistic locking wykryje konflikt przy zapisie.
             */
            if (pool.getAvailableCapacity() <= 0) {
                throw new CapacityUnavailableException("No available capacity for event: " + eventId);
            }

            /*
             * Zmniejszamy dostępność w encji.
             *
             * Hibernate przygotuje UPDATE zawierający warunek po wersji,
             * np. logicznie:
             *
             * UPDATE capacity_pools
             * SET available_capacity = ?, version = version + 1
             * WHERE id = ?
             *   AND version = ?
             *
             * Jeśli version już się zmieniła, UPDATE dotknie 0 wierszy i powstanie
             * optimistic locking exception.
             */
            pool.reserveOne();

            /*
             * Zapisujemy rezerwację.
             *
             * Ważny niuans:
             * jeśli później flush CapacityPool zakończy się optimistic conflict,
             * cała transakcja powinna zostać wycofana, więc ta rezerwacja też
             * nie zostanie trwale zapisana.
             */
            UUID reservationId = reservationCreationSupport.saveReservation(event, request).getId();

            /*
             * Wymuszamy flush teraz, a nie dopiero przy commit.
             *
             * Dzięki temu konflikt @Version wychodzi wewnątrz createInTransaction(...)
             * i może zostać złapany przez pętlę retry w create(...).
             */
            capacityPoolRepository.flush();

            return reservationId;
        } catch (OptimisticLockingFailureException e) {
            /*
             * Oznaczamy transakcję do rollbacku i przekazujemy wyjątek wyżej.
             *
             * Kolejna próba retry zostanie uruchomiona w nowej transakcji.
             */
            status.setRollbackOnly();
            throw e;
        }
    }
}