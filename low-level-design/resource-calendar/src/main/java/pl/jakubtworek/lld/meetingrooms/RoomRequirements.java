package pl.jakubtworek.lld.meetingrooms;

import java.util.Set;

/**
 * Wymagania są wartością należącą do spotkania, nie zmieniają katalogu sal.
 * Kopia zbioru chroni także przed późniejszym dopisaniem wymogu przez kod
 * wywołujący. Pusty zbiór oznacza brak wymagań sprzętowych, nie brak spotkania.
 */
public record RoomRequirements(int attendees, Set<Equipment> equipment) {

    public RoomRequirements {
        if (attendees <= 0) {
            throw new IllegalArgumentException("Spotkanie wymaga co najmniej jednej osoby");
        }
        equipment = Set.copyOf(equipment);
    }
}
