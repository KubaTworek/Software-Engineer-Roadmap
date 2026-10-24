package pl.jakubtworek.lld.parkinglot;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Stabilny widok wszystkich miejsc i biletów. Konstruktor jest pakietowy;
 * zewnętrzny klient nie zapisze tu własnej „historii”. Kolekcje są kopiowane
 * i niemodyfikowalne, a ich elementy niemutowalne. Samo final przy Map nie
 * wystarczyłoby: klient mógłby inaczej usunąć aktywny bilet przez getter.
 *
 * Aktywność, zajętość i dostępność wyliczamy z jednego źródła — biletów.
 * To upraszcza naukę niezmienników kosztem skanowania historii przy odczycie.
 * Widok nie oferuje równości strukturalnej; == oznacza tę samą publikację.
 */
public final class ParkingSnapshot {

    private final List<ParkingSpot> spots;
    private final Map<TicketId, ParkingTicket> tickets;
    private final Optional<Instant> lastOperationAt;

    ParkingSnapshot(List<ParkingSpot> spots, Map<TicketId, ParkingTicket> tickets, Optional<Instant> lastOperationAt) {
        this.spots = List.copyOf(spots);
        this.tickets = Map.copyOf(tickets);
        this.lastOperationAt = java.util.Objects.requireNonNull(lastOperationAt, "lastOperationAt");
    }

    public List<ParkingSpot> spots() {
        return spots;
    }

    public Map<TicketId, ParkingTicket> tickets() {
        return tickets;
    }

    public Optional<Instant> lastOperationAt() {
        return lastOperationAt;
    }

    /**
     * Pytanie o istnienie nie wymaga porządku prezentacji. Skan zatrzymuje się
     * na pierwszym pasującym aktywnym bilecie i nie tworzy posortowanej listy.
     * Koszt O(T) w najgorszym przypadku, bez dodatkowego indeksu do utrzymywania.
     * Bilety zamknięte pozostają w historii, ale nie blokują ponownego wjazdu.
     */
    public boolean hasActiveVehicle(LicensePlate plate) {
        java.util.Objects.requireNonNull(plate, "plate");
        return tickets.values().stream().anyMatch(ticket -> ticket.isActive() && ticket.vehicle().plate()
                .equals(plate));
    }

    public List<ParkingTicket> activeTickets() {
        // Map.copyOf nie obiecuje kolejności iteracji. Jawne sortowanie zapewnia
        // deterministyczny odczyt aktywnych biletów, niezależny od implementacji mapy.
        return tickets.values().stream().filter(ParkingTicket::isActive)
                .sorted(Comparator.comparingLong(ticket -> ticket.id().number())).toList();
    }

    public List<ParkingSpot> availableSpots() {
        Set<String> occupied = tickets.values().stream().filter(ParkingTicket::isActive).map(ticket -> ticket.spot()
                .id()).collect(Collectors.toSet());
        return spots.stream().filter(spot -> !occupied.contains(spot.id())).toList();
    }
}
