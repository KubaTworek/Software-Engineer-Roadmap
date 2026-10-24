package pl.jakubtworek.lld.parkinglot;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Bilet to migawka pojedynczego postoju. Brak rachunku oznacza aktywny postój;
 * obecność rachunku oznacza zamknięcie. Nie dokładamy flagi closed i osobnej
 * mutowalnej kwoty, bo mogłyby utworzyć kombinację „aktywny i już rozliczony”.
 * Konstrukcja oraz zamknięcie są wewnętrzne dla domeny; publiczny klient tylko
 * odczytuje bilet. Zamknięcie tworzy nową instancję, więc stary widok nie znika.
 */
public final class ParkingTicket {

    private final TicketId id;
    private final Vehicle vehicle;
    private final ParkingSpot spot;
    private final Instant enteredAt;
    private final Optional<ParkingReceipt> receipt;

    private ParkingTicket(TicketId id, Vehicle vehicle, ParkingSpot spot, Instant enteredAt,
            Optional<ParkingReceipt> receipt) {
        this.id = Objects.requireNonNull(id, "id");
        this.vehicle = Objects.requireNonNull(vehicle, "vehicle");
        this.spot = Objects.requireNonNull(spot, "spot");
        this.enteredAt = Objects.requireNonNull(enteredAt, "enteredAt");
        this.receipt = Objects.requireNonNull(receipt, "receipt");
    }

    static ParkingTicket open(TicketId id, Vehicle vehicle, ParkingSpot spot, Instant enteredAt) {
        return new ParkingTicket(id, vehicle, spot, enteredAt, Optional.empty());
    }

    ParkingTicket close(Instant exitedAt, Money fee) {
        if (!isActive()) {
            throw new ParkingException(ParkingException.Reason.TICKET_ALREADY_CLOSED, "Bilet został już zamknięty");
        }
        var settlement = new ParkingReceipt(id, enteredAt, exitedAt, fee);
        return new ParkingTicket(id, vehicle, spot, enteredAt, Optional.of(settlement));
    }

    public TicketId id() {
        return id;
    }

    public Vehicle vehicle() {
        return vehicle;
    }

    public ParkingSpot spot() {
        return spot;
    }

    public Instant enteredAt() {
        return enteredAt;
    }

    public Optional<ParkingReceipt> receipt() {
        return receipt;
    }

    public boolean isActive() {
        return receipt.isEmpty();
    }
}
