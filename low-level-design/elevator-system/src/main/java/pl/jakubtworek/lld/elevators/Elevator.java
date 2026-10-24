package pl.jakubtworek.lld.elevators;

import java.util.List;
import java.util.Objects;
import static pl.jakubtworek.lld.elevators.ElevatorException.Reason.*;

/**
 * Niemutowalna kabina jest właścicielem kolejki i lokalnych przejść ruchu,
 * drzwi oraz trybu. Metody zmieniające są pakietowe: zespół publikuje kabinę
 * razem z przydziałami wezwań. Samodzielna podmiana listy z zewnątrz mogłaby
 * zgubić wezwanie, które dyspozytor nadal uważa za przyjęte.
 * To dyskretna symulacja: nie modelujemy prędkości ani ruchu między piętrami.
 */
public final class Elevator {

    private final ElevatorId id;
    private final Building building;
    private final int floor;
    private final Direction direction;
    private final DoorState doors;
    private final ServiceMode mode;
    private final StopQueue stops;

    public Elevator(ElevatorId id, Building building, int initialFloor) {
        this(id, building, initialFloor, Direction.IDLE, DoorState.CLOSED, ServiceMode.IN_SERVICE, StopQueue.empty());
    }

    private Elevator(ElevatorId id, Building building, int floor, Direction direction, DoorState doors,
            ServiceMode mode, StopQueue stops) {
        this.id = Objects.requireNonNull(id, "id");
        this.building = Objects.requireNonNull(building, "building");
        building.requireFloor(floor);
        this.floor = floor;
        this.direction = direction;
        this.doors = doors;
        this.mode = mode;
        this.stops = stops;
    }

    Elevator requestStop(int target) {
        building.requireFloor(target);
        if (mode == ServiceMode.OUT_OF_SERVICE) {
            throw new ElevatorException(OUT_OF_SERVICE, "Winda jest wyłączona z obsługi");
        }
        if (mode == ServiceMode.DRAINING) {
            throw new ElevatorException(DRAINING, "Winda kończy przyjęte cele i nie przyjmuje poleceń wyboru piętra");
        }
        // Przy już otwartych drzwiach na tym piętrze odbiór jest natychmiastowy.
        // Przy zamkniętych dokładamy cel, żeby następny tick rzeczywiście otworzył drzwi.
        if (stops.contains(target) || doors == DoorState.OPEN && floor == target) {
            return this;
        }
        var next = stops.append(target);
        return copy(floor, direction, doors, mode, next);
    }

    Elevator tick() {
        if (mode == ServiceMode.OUT_OF_SERVICE) {
            return this;
        }
        if (doors == DoorState.OPEN) {
            // Ostatni odbiór nie kończy wycofania: drzwi trzeba jeszcze zamknąć.
            // Tryb i drzwi zmieniają się w jednym kandydacie, bez stanu pośredniego.
            var nextMode = mode == ServiceMode.DRAINING && stops.isEmpty() ? ServiceMode.OUT_OF_SERVICE : mode;
            return copy(floor, Direction.IDLE, DoorState.CLOSED, nextMode, stops);
        }
        if (stops.isEmpty()) {
            return this;
        }
        int target = stops.getFirst();
        // Integer.compare nie przepełnia się dla MIN_VALUE/MAX_VALUE. Krok
        // mieści się w budynku, bo zawsze zbliża się do zwalidowanego celu.
        int step = Integer.compare(target, floor);
        int nextFloor = Math.addExact(floor, step);
        if (nextFloor == target) {
            // Obsługujemy tylko głowę FIFO. Przejechanie po drodze przez inny
            // zamówiony przystanek nie realizuje go wcześniej. To świadomie
            // prostsza reguła niż kolektywne sterowanie kierunkowe LOOK/SCAN.
            return copy(nextFloor, Direction.IDLE, DoorState.OPEN, mode, stops.tail());
        }
        return copy(nextFloor, step > 0 ? Direction.UP : Direction.DOWN, DoorState.CLOSED, mode, stops);
    }

    Elevator takeOutOfService() {
        if (mode == ServiceMode.OUT_OF_SERVICE) {
            throw new ElevatorException(ALREADY_OUT_OF_SERVICE, "Winda już wyłączona");
        }
        if (!stops.isEmpty() || doors != DoorState.CLOSED || direction != Direction.IDLE) {
            throw new ElevatorException(NOT_IDLE, "Przed wyłączeniem opróżnij kolejkę i zamknij drzwi");
        }
        return copy(floor, direction, doors, ServiceMode.OUT_OF_SERVICE, stops);
    }

    Elevator returnToService() {
        if (mode == ServiceMode.IN_SERVICE) {
            throw new ElevatorException(ALREADY_IN_SERVICE, "Winda już obsługuje żądania");
        }
        return copy(floor, direction, doors, ServiceMode.IN_SERVICE, stops);
    }

    /**
     * Planowane wycofanie nie kasuje kolejki ani nie przenosi wezwań. Bezczynna
     * zamknięta kabina może od razu przejść do OUT_OF_SERVICE. in może cofnąć
     * DRAINING, zachowując aktualny ruch, drzwi i kolejność przyjętych celów.
     */
    Elevator beginDraining() {
        if (mode == ServiceMode.OUT_OF_SERVICE) {
            throw new ElevatorException(OUT_OF_SERVICE, "Winda już wyłączona");
        }
        if (mode == ServiceMode.DRAINING) {
            throw new ElevatorException(ALREADY_DRAINING, "Wycofywanie już trwa");
        }
        var nextMode = stops.isEmpty() && doors == DoorState.CLOSED
                && direction == Direction.IDLE ? ServiceMode.OUT_OF_SERVICE : ServiceMode.DRAINING;
        return copy(floor, direction, doors, nextMode, stops);
    }

    private Elevator copy(int nextFloor, Direction nextDirection, DoorState nextDoors, ServiceMode nextMode,
            StopQueue nextStops) {
        return new Elevator(id, building, nextFloor, nextDirection, nextDoors, nextMode, nextStops);
    }

    public ElevatorId id() {
        return id;
    }

    public Building building() {
        return building;
    }

    public int floor() {
        return floor;
    }

    public Direction direction() {
        return direction;
    }

    public DoorState doors() {
        return doors;
    }

    public ServiceMode mode() {
        return mode;
    }

    public List<Integer> stops() {
        return stops;
    }
}
