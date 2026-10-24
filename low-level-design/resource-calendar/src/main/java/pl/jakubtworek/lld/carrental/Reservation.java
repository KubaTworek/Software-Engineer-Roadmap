package pl.jakubtworek.lld.carrental;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import static pl.jakubtworek.lld.carrental.RentalException.Reason.*;

/**
 * Niemutowalna umowa i właściciel legalnych przejść. Konstruktor prywatny:
 * klient API nie zbuduje ACTIVE bez odbioru ani COMPLETED bez rozliczenia.
 * Każda metoda tworzy nową wersję tego samego ID, stary widok zachowuje dane.
 * Konflikty z innymi umowami i serwisem wymagają wiedzy RentalAgency.
 */
public final class Reservation {

    private final ReservationId id;
    private final VehicleId vehicleId;
    private final CustomerId customerId;
    private final RentalPeriod period;
    private final Money dailyRate;
    private final Money quotedTotal;
    private final ReservationStatus status;
    private final Optional<RentalReceipt> receipt;

    private Reservation(ReservationId id, VehicleId vehicleId, CustomerId customerId, RentalPeriod period,
            Money dailyRate, Money quotedTotal, ReservationStatus status, Optional<RentalReceipt> receipt) {
        this.id = id;
        this.vehicleId = vehicleId;
        this.customerId = customerId;
        this.period = period;
        this.dailyRate = dailyRate;
        this.quotedTotal = quotedTotal;
        this.status = status;
        this.receipt = receipt;
    }

    static Reservation reserved(ReservationId id, Vehicle vehicle, CustomerId customerId, RentalPeriod period) {
        Money rate = vehicle.category().dailyRate();
        return new Reservation(id, vehicle.id(), customerId, period, rate, rate.times(period.days()),
                ReservationStatus.RESERVED, Optional.empty());
    }

    Reservation pickup(LocalDate today) {
        requireStatus(ReservationStatus.RESERVED);
        if (!today.equals(period.start())) {
            throw new RentalException(PICKUP_DATE_MISMATCH, "Odbiór wymaga daty początku rezerwacji");
        }
        return version(ReservationStatus.ACTIVE, Optional.empty());
    }

    Reservation cancel() {
        requireStatus(ReservationStatus.RESERVED);
        return version(ReservationStatus.CANCELLED, Optional.empty());
    }

    /**
     * Zmiana terminu pozostaje tą samą umową i zachowuje zapisaną stawkę.
     * Przeliczamy pełną cenę nowego okresu, nie różnicę względem starej ceny.
     * Konflikty i bieżącą datę sprawdza agencja przed publikacją tej wersji.
     */
    Reservation rescheduled(RentalPeriod replacement) {
        requireStatus(ReservationStatus.RESERVED);
        return new Reservation(id, vehicleId, customerId, replacement, dailyRate, dailyRate.times(replacement.days()),
                ReservationStatus.RESERVED, Optional.empty());
    }

    Reservation complete(LocalDate today) {
        requireStatus(ReservationStatus.ACTIVE);
        // Odbiór był dokładnie w period.start(). Cofnięty zegar nie może
        // utworzyć zwrotu sprzed fizycznego wydania auta.
        if (today.isBefore(period.start())) {
            throw new IllegalStateException("Zegar wskazuje datę przed odbiorem");
        }
        long extraDays = Math.max(0, ChronoUnit.DAYS.between(period.end(), today));
        var result = new RentalReceipt(id, today, quotedTotal, dailyRate.times(extraDays));
        return version(ReservationStatus.COMPLETED, Optional.of(result));
    }

    void requireStatus(ReservationStatus expected) {
        if (status != expected) {
            throw new RentalException(INVALID_STATUS, "Operacja wymaga stanu " + expected + ", obecny: " + status);
        }
    }

    boolean blocks(RentalPeriod candidate) {
        return (status == ReservationStatus.RESERVED || status == ReservationStatus.ACTIVE)
                && period.overlaps(candidate);
    }

    private Reservation version(ReservationStatus nextStatus, Optional<RentalReceipt> result) {
        return new Reservation(id, vehicleId, customerId, period, dailyRate, quotedTotal, nextStatus, result);
    }

    public ReservationId id() {
        return id;
    }

    public VehicleId vehicleId() {
        return vehicleId;
    }

    public CustomerId customerId() {
        return customerId;
    }

    public RentalPeriod period() {
        return period;
    }

    public Money dailyRate() {
        return dailyRate;
    }

    public Money quotedTotal() {
        return quotedTotal;
    }

    public ReservationStatus status() {
        return status;
    }

    public Optional<RentalReceipt> receipt() {
        return receipt;
    }
}
