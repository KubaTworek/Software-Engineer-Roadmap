package pl.jakubtworek.lld.parkinglot;

import java.time.*;
import java.util.*;
import static pl.jakubtworek.lld.parkinglot.ParkingException.Reason.*;

/**
 * Rdzeń indeksowany: bilety HashMap, aktywne rejestracje HashMap i wolne miejsca
 * w TreeSet per typ. Historia nie jest kopiowana przy enter/exit.
 * Snapshot powstaje leniwie i jest unieważniany dopiero po poprawnej operacji.
 * Wszystkie wywołania zależności i walidacje poprzedzają aktualizację indeksów.
 * Kontrakt pozostaje jednowątkowy; struktury nie są blokadą.
 */
public final class ParkingLot {

    private final UUID id = UUID.randomUUID();
    private final SpotAllocationStrategy allocation;
    private final FeePolicy fees;
    private final Clock clock;
    private final List<ParkingSpot> spots;
    private final Map<TicketId, ParkingTicket> tickets = new HashMap<>();
    private final Map<LicensePlate, ParkingTicket> active = new HashMap<>();
    private final FreeSpotIndex free;
    private Instant lastOperationAt;
    private ParkingSnapshot cached;

    public ParkingLot(List<ParkingSpot> spots, SpotAllocationStrategy allocation, FeePolicy fees, Clock clock) {
        this.allocation = Objects.requireNonNull(allocation);
        this.fees = Objects.requireNonNull(fees);
        this.clock = Objects.requireNonNull(clock);
        var copy = List.copyOf(spots);
        var ids = new HashSet<String>();
        if (copy.isEmpty() || copy.stream().anyMatch(s -> !ids.add(s.id())))
            throw new IllegalArgumentException("Parking wymaga miejsc o unikalnych ID");
        this.spots = copy.stream().sorted(Comparator.comparingInt(ParkingSpot::floor).thenComparing(ParkingSpot::id))
                .toList();
        free = new FreeSpotIndex(this.spots);
    }

    public UUID id() {
        return id;
    }

    // Odczyt zmienia leniwy cache. final przy mapach nie zamraża ich wpisów;
    // cała instancja, łącznie ze snapshot(), wymaga jednego wątku.
    public ParkingSnapshot snapshot() {
        if (cached == null)
            cached = new ParkingSnapshot(spots, tickets, Optional.ofNullable(lastOperationAt));
        return cached;
    }

    public ParkingTicket enter(Vehicle vehicle) {
        Objects.requireNonNull(vehicle);
        if (active.containsKey(vehicle.plate()))
            throw new ParkingException(VEHICLE_ALREADY_PARKED, "Pojazd ma już aktywny bilet");
        var choice = Objects.requireNonNull(allocation.select(free, vehicle.type()), "allocation result");
        if (choice.isEmpty() && free.firstAvailable(vehicle.type()).isPresent() || choice.isPresent()
                && (!free.contains(choice.get()) || !choice.get().type().accommodates(vehicle.type())))
            throw new IllegalStateException("Strategia zwróciła wynik spoza kontraktu kandydatów");
        var spot = choice.orElseThrow(() -> new ParkingException(NO_COMPATIBLE_SPOT,
                "Brak wolnego miejsca zgodnego z typem pojazdu"));
        Instant now = operationTime();
        var ticket = ParkingTicket.open(new TicketId(id, Math.addExact((long) tickets.size(), 1)), vehicle, spot, now);
        // Trzy indeksy opisują jeden postój. Osobne kolekcje współbieżne
        // nie uczyniłyby sekwencji sprawdzenie–przydział–zapis atomową.
        tickets.put(ticket.id(), ticket);
        active.put(vehicle.plate(), ticket);
        free.occupy(spot);
        lastOperationAt = now;
        cached = null;
        return ticket;
    }

    public ParkingReceipt exit(TicketId ticketId) {
        Objects.requireNonNull(ticketId);
        var ticket = tickets.get(ticketId);
        if (ticket == null)
            throw new ParkingException(UNKNOWN_TICKET, "Bilet nie należy do historii tego parkingu");
        if (!ticket.isActive())
            throw new ParkingException(TICKET_ALREADY_CLOSED, "Bilet został już zamknięty");
        Instant now = operationTime();
        var context = new PricingContext(ticket.vehicle().type(), ticket.spot().type(), ticket.enteredAt(), now);
        Money fee = Objects.requireNonNull(fees.calculate(context), "fee result");
        var closed = ticket.close(now, fee);
        tickets.put(ticketId, closed);
        active.remove(ticket.vehicle().plate());
        free.release(ticket.spot());
        lastOperationAt = now;
        cached = null;
        return closed.receipt().orElseThrow();
    }

    private Instant operationTime() {
        Instant now = Objects.requireNonNull(clock.instant());
        if (lastOperationAt != null && now.isBefore(lastOperationAt))
            throw new ParkingException(CLOCK_MOVED_BACKWARDS, "Zegar cofnął się względem ostatniej operacji");
        return now;
    }
}
