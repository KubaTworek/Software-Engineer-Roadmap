package pl.jakubtworek.lld.elevators;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import static pl.jakubtworek.lld.elevators.ElevatorException.Reason.*;

/**
 * Zespół jest granicą spójności przydziałów i kabin. Kabina zna swój ruch,
 * dyspozytor preferencję wyboru, a grupa pilnuje jednego odbioru danego piętra.
 * Model jednowątkowy w pamięci: kopia przed publikacją nie zastępuje blokady
 * współbieżnej ani transakcji bazy. Symulacja postępuje wyłącznie przez tick.
 */
public final class ElevatorGroup {

    private final Building building;
    private final DispatchPolicy dispatch;
    private ElevatorSnapshot state;

    public ElevatorGroup(Building building, List<Elevator> cars, DispatchPolicy dispatch) {
        this.building = Objects.requireNonNull(building, "building");
        this.dispatch = Objects.requireNonNull(dispatch, "dispatch");
        var known = new HashMap<ElevatorId, Elevator>();
        for (var car : List.copyOf(cars)) {
            if (!car.building().equals(building)) {
                throw new IllegalArgumentException("Windy muszą obsługiwać ten sam budynek");
            }
            if (known.putIfAbsent(car.id(), car) != null) {
                throw new IllegalArgumentException("Powtórzony kod windy");
            }
        }
        if (known.isEmpty()) {
            throw new IllegalArgumentException("Zespół wymaga co najmniej jednej windy");
        }
        // Początkowy katalog przyjmujemy tylko w stanie bez oczekujących celów.
        // Nie importujemy kabiny z kolejką, dla której nie znamy przydziałów wezwań.
        if (known.values().stream().anyMatch(car -> !car.stops().isEmpty() || car.doors() != DoorState.CLOSED
                || car.direction() != Direction.IDLE || car.mode() == ServiceMode.DRAINING)) {
            throw new IllegalArgumentException("Początkowe windy muszą być bezczynne z zamkniętymi drzwiami");
        }
        state = new ElevatorSnapshot(known, Map.of());
    }

    public ElevatorSnapshot snapshot() {
        return state;
    }

    public Building building() {
        return building;
    }

    public ElevatorId call(int floor) {
        building.requireFloor(floor);
        var assigned = state.hallCalls().get(floor);
        if (assigned != null) {
            return assigned;
        }
        var available = state.cars().values().stream().filter(car -> car.mode() == ServiceMode.IN_SERVICE)
                .sorted(Comparator.comparing(car -> car.id().value())).toList();
        if (available.isEmpty()) {
            throw new ElevatorException(NO_AVAILABLE_ELEVATOR, "Brak wind przyjmujących nowe wezwania");
        }
        var selected = dispatch.select(available, floor);
        if (selected == null || available.stream().noneMatch(car -> car.id().equals(selected))) {
            throw new IllegalStateException("Dyspozytor wybrał windę spoza kandydatów");
        }
        var car = state.cars().get(selected);
        var next = car.requestStop(floor);
        if (car.doors() == DoorState.OPEN && car.floor() == floor) {
            return selected;
        }
        var calls = new HashMap<>(state.hallCalls());
        calls.put(floor, selected);
        var cars = new HashMap<>(state.cars());
        cars.put(selected, next);
        state = new ElevatorSnapshot(cars, calls);
        return selected;
    }

    public void selectFloor(ElevatorId id, int floor) {
        replace(known(id).requestStop(floor));
    }

    public void takeOutOfService(ElevatorId id) {
        replace(known(id).takeOutOfService());
    }

    public void returnToService(ElevatorId id) {
        replace(known(id).returnToService());
    }

    public void beginDraining(ElevatorId id) {
        replace(known(id).beginDraining());
    }

    public long estimatePickup(ElevatorId id, int floor) {
        return PickupEstimator.stepsToPickup(known(id), floor);
    }

    /**
     * Jeden wspólny krok wszystkich kabin, bez zależności od kolejności iteracji mapy.
     */
    public TickResult tick() {
        var cars = new HashMap<ElevatorId, Elevator>();
        state.cars().forEach((id, car) -> cars.put(id, car.tick()));
        var calls = new HashMap<>(state.hallCalls());
        // Usuwamy wezwanie dopiero po otwarciu drzwi przypisanej windy.
        // Samo przejechanie przez piętro lub przyjazd innej kabiny nie wystarcza.
        calls.entrySet().removeIf(entry -> {
            var car = cars.get(entry.getValue());
            return car.doors() == DoorState.OPEN && car.floor() == entry.getKey();
        });
        var events = new ArrayList<ElevatorEvent>();
        // Stabilna kolejność: kod kabiny, następnie ruch, drzwi, obsłużone
        // wezwanie, tryb. Kolejność mapy ani liczba kabin nie zmienia śladu.
        state.cars().values().stream().sorted(Comparator.comparing(car -> car.id().value())).forEach(old -> {
            var next = cars.get(old.id());
            if (old.floor() != next.floor()) {
                events.add(new ElevatorEvent.Moved(old.id(), old.floor(), next.floor()));
            }
            if (old.doors() != next.doors()) {
                events.add(next.doors() == DoorState.OPEN ? new ElevatorEvent.DoorsOpened(old.id(),
                        next.floor()) : new ElevatorEvent.DoorsClosed(old.id(), next.floor()));
            }
            if (old.id().equals(state.hallCalls().get(next.floor())) && !calls.containsKey(next.floor())) {
                events.add(new ElevatorEvent.HallCallServed(old.id(), next.floor()));
            }
            if (old.mode() != next.mode()) {
                events.add(new ElevatorEvent.ModeChanged(old.id(), old.mode(), next.mode()));
            }
        });
        var candidate = cars.equals(state.cars())
                && calls.equals(state.hallCalls()) ? state : new ElevatorSnapshot(cars, calls);
        // Najpierw kompletny wynik z kopiowaną listą zdarzeń, dopiero publikacja.
        // Odbiorca nie dostanie faktów odnoszących się do nieopublikowanego stanu.
        var result = new TickResult(candidate, events);
        state = candidate;
        return result;
    }

    private Elevator known(ElevatorId id) {
        Objects.requireNonNull(id, "id");
        var car = state.cars().get(id);
        if (car == null) {
            throw new ElevatorException(UNKNOWN_ELEVATOR, "Nieznany kod windy");
        }
        return car;
    }

    private void replace(Elevator next) {
        if (state.cars().get(next.id()) == next) {
            return;
        }
        var cars = new HashMap<>(state.cars());
        cars.put(next.id(), next);
        state = new ElevatorSnapshot(cars, state.hallCalls());
    }
}
