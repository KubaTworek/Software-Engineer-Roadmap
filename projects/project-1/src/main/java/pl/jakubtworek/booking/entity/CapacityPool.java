package pl.jakubtworek.booking.entity;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * Encja JPA reprezentująca pulę dostępności miejsc dla jednego eventu.
 *
 * CapacityPool została wydzielona z Event celowo.
 *
 * Event opisuje wydarzenie:
 * - nazwa,
 * - miasto,
 * - kategoria,
 * - data.
 *
 * CapacityPool opisuje stan rezerwacyjny:
 * - całkowita liczba miejsc,
 * - aktualnie dostępna liczba miejsc,
 * - wersja do optimistic lockingu.
 *
 * To rozdzielenie jest praktyczne, bo dostępność miejsc jest krytycznym obszarem
 * concurrency. Dzięki osobnej encji łatwiej testować race condition, lost update,
 * optimistic locking, pessimistic locking i atomowy SQL update.
 */
@Entity
@Table(name = "capacity_pools")
public class CapacityPool {

    /**
     * Główny identyfikator puli miejsc.
     *
     * UUID jest generowany aplikacyjnie w konstruktorze.
     */
    @Id
    private UUID id;

    /**
     * Event, którego dotyczy ta pula dostępności.
     *
     * Relacja OneToOne oznacza:
     * - jeden event ma dokładnie jedną pulę miejsc,
     * - jedna pula miejsc należy do dokładnie jednego eventu.
     *
     * optional = false oznacza, że CapacityPool nie powinna istnieć bez eventu.
     *
     * @JoinColumn:
     * - name = "event_id" — nazwa kolumny FK w tabeli capacity_pools,
     * - nullable = false — baza nie powinna dopuścić NULL-a,
     * - unique = true — jeden event nie może mieć wielu pul miejsc.
     *
     * FetchType.LAZY jest użyty po to, żeby nie pobierać Event automatycznie za
     * każdym razem, gdy pobierasz CapacityPool.
     */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false, unique = true)
    private Event event;

    /**
     * Całkowita liczba miejsc.
     *
     * Ta wartość nie zmienia się przy każdej rezerwacji. Jest punktem odniesienia
     * dla availableCapacity.
     *
     * Przykład:
     * - totalCapacity = 100,
     * - availableCapacity = 80,
     * - oznacza to, że 20 miejsc jest aktualnie zajętych.
     */
    @Column(nullable = false)
    private int totalCapacity;

    /**
     * Aktualnie dostępna liczba miejsc.
     *
     * To pole jest najbardziej wrażliwe na błędy współbieżności.
     *
     * Naiwny kod typu:
     *
     * if (pool.getAvailableCapacity() > 0) {
     *     pool.reserveOne();
     * }
     *
     * może być niebezpieczny, jeśli wiele requestów działa równolegle.
     *
     * Dlatego w produkcyjnym flow projektu lepiej używać atomowego SQL update
     * z CapacityPoolRepository.reserveOneSeatIfAvailable(...).
     */
    @Column(nullable = false)
    private int availableCapacity;

    /**
     * Pole wersji używane przez Hibernate do optimistic lockingu.
     *
     * @Version powoduje, że Hibernate sprawdza, czy encja nie została zmieniona
     * przez inną transakcję między odczytem a zapisem.
     *
     * Przykład:
     *
     * 1. Transakcja A czyta CapacityPool z version = 1.
     * 2. Transakcja B czyta CapacityPool z version = 1.
     * 3. Transakcja A zapisuje zmianę i version rośnie do 2.
     * 4. Transakcja B próbuje zapisać zmianę z version = 1.
     * 5. Hibernate wykrywa konflikt i rzuca optimistic locking exception.
     *
     * To dobra strategia, gdy konflikty są rzadkie.
     * Przy bardzo dużej konkurencji może powodować dużo retry.
     */
    @Version
    private long version;

    /**
     * Konstruktor bezargumentowy wymagany przez JPA.
     *
     * Jest protected, żeby kod aplikacyjny nie tworzył pustej, niepoprawnej puli.
     */
    protected CapacityPool() {
    }

    /**
     * Tworzy nową pulę miejsc dla eventu.
     *
     * totalCapacity musi być dodatnie. Pula z zerową albo ujemną pojemnością
     * nie ma sensu biznesowego w tym modelu.
     *
     * availableCapacity startuje z wartością totalCapacity, bo na początku
     * wszystkie miejsca są dostępne.
     */
    public CapacityPool(Event event, int totalCapacity) {
        if (totalCapacity <= 0) {
            throw new IllegalArgumentException("totalCapacity must be positive");
        }

        this.id = UUID.randomUUID();
        this.event = event;
        this.totalCapacity = totalCapacity;
        this.availableCapacity = totalCapacity;
    }

    /**
     * Rezerwuje jedno miejsce w modelu obiektowym.
     *
     * Ta metoda pilnuje lokalnej reguły:
     * nie można zejść poniżej zera.
     *
     * Ważne:
     * sama ta metoda nie rozwiązuje problemu concurrency, jeśli wiele transakcji
     * równolegle pracuje na tej samej puli.
     *
     * Do demonstracji optimistic lockingu jest przydatna.
     * Do głównego flow odpornego na overselling lepszy jest atomowy SQL update.
     */
    public void reserveOne() {
        if (availableCapacity <= 0) {
            throw new IllegalStateException("No available capacity");
        }

        this.availableCapacity--;
    }

    /**
     * Zwalnia jedno miejsce.
     *
     * Ta metoda pilnuje lokalnej reguły:
     * dostępność nie może przekroczyć całkowitej pojemności.
     *
     * Przykład błędu, przed którym chroni:
     * - totalCapacity = 10,
     * - availableCapacity = 10,
     * - aplikacja próbuje jeszcze raz zwolnić miejsce,
     * - bez tej walidacji zrobiłoby się 11/10.
     */
    public void releaseOne() {
        if (availableCapacity >= totalCapacity) {
            throw new IllegalStateException("Capacity cannot exceed total capacity");
        }

        this.availableCapacity++;
    }

    /**
     * Gettery udostępniają stan encji do mapowania DTO i testów.
     *
     * Brak publicznych setterów jest celowy.
     * Zmiana dostępności powinna przechodzić przez metody domenowe albo
     * kontrolowane zapytania repozytorium, a nie przez dowolne setAvailableCapacity(...).
     */
    public UUID getId() {
        return id;
    }

    public Event getEvent() {
        return event;
    }

    public int getTotalCapacity() {
        return totalCapacity;
    }

    public int getAvailableCapacity() {
        return availableCapacity;
    }

    public long getVersion() {
        return version;
    }
}