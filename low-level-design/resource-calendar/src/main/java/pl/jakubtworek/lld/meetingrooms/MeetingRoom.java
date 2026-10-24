package pl.jakubtworek.lld.meetingrooms;

import java.util.Objects;
import java.util.Set;

/**
 * Sala odpowiada za zgodność swoich stałych cech z wymaganiem. Nie przechowuje
 * drugiej, niezależnej listy rezerwacji: kalendarz ma jednego właściciela.
 * Wspólna ocena cech w RoomAvailability wyznacza różnicę zbiorów: dodatkowy
 * projektor nie dyskwalifikuje sali wymagającej tylko tablicy. Pojemność jest
 * limitem, nie liczbą wolnych krzeseł. satisfies to ta sama ocena bez kalendarza.
 */
public record MeetingRoom(RoomId id, String name, int capacity, Set<Equipment> equipment) {

    public MeetingRoom {
        Objects.requireNonNull(id, "id");
        name = Objects.requireNonNull(name, "name").strip();
        if (name.isEmpty() || capacity <= 0) {
            throw new IllegalArgumentException("Sala wymaga nazwy i dodatniej pojemności");
        }
        equipment = Set.copyOf(equipment);
    }

    public boolean satisfies(RoomRequirements requirements) {
        Objects.requireNonNull(requirements, "requirements");
        return RoomAvailability.assess(this, requirements, java.util.List.of()).available();
    }
}
