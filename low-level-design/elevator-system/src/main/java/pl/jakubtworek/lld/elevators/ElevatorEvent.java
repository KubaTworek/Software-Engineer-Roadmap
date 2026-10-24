package pl.jakubtworek.lld.elevators;

import java.util.Objects;

/**
 * Fakty jednego tick, a nie komendy ani trwały dziennik. Ruch i otwarcie mogą
 * wystąpić w jednym kroku. HallCallServed dotyczy tylko przydzielonego wezwania;
 * zwykły cel kabiny także otwiera drzwi, lecz nie tworzy fałszywego wezwania.
 * Warianty są zamknięte, więc konsola musi jawnie obsłużyć każdy rodzaj faktu.
 */
public sealed interface ElevatorEvent {

    ElevatorId car();

    record Moved(ElevatorId car, int from, int to) implements ElevatorEvent {

        public Moved {
            Objects.requireNonNull(car, "car");
            if (Math.abs((long) to - from) != 1) {
                throw new IllegalArgumentException("Ruch musi obejmować jedno piętro");
            }
        }
    }

    record DoorsOpened(ElevatorId car, int floor) implements ElevatorEvent {

        public DoorsOpened {
            Objects.requireNonNull(car, "car");
        }
    }

    record DoorsClosed(ElevatorId car, int floor) implements ElevatorEvent {

        public DoorsClosed {
            Objects.requireNonNull(car, "car");
        }
    }

    record HallCallServed(ElevatorId car, int floor) implements ElevatorEvent {

        public HallCallServed {
            Objects.requireNonNull(car, "car");
        }
    }

    record ModeChanged(ElevatorId car, ServiceMode from, ServiceMode to) implements ElevatorEvent {

        public ModeChanged {
            Objects.requireNonNull(car, "car");
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(to, "to");
            if (from == to) {
                throw new IllegalArgumentException("Zdarzenie wymaga zmiany trybu");
            }
        }
    }
}
