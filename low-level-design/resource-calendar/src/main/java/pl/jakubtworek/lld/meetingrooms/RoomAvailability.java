package pl.jakubtworek.lld.meetingrooms;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Wyjaśnienie oceny jednej sali: powody mogą wystąpić JEDNOCZEŚNIE.
 * Mała sala bez projektora może dodatkowo być zajęta. Nie tracimy pozostałych
 * przyczyn po pierwszym niespełnionym warunku. Konflikty zawierają konkretne
 * ID spotkań, także gdy przecina się tylko ich bufor przygotowania.
 */
public record RoomAvailability(MeetingRoom room, boolean insufficientCapacity, Set<Equipment> missingEquipment,
        List<BookingId> conflicts) {

    public RoomAvailability {
        Objects.requireNonNull(room, "room");
        missingEquipment = Set.copyOf(missingEquipment);
        conflicts = List.copyOf(conflicts);
    }

    static RoomAvailability assess(MeetingRoom room, RoomRequirements requirements, List<BookingId> conflicts) {
        Objects.requireNonNull(requirements, "requirements");
        var missing = new HashSet<>(requirements.equipment());
        missing.removeAll(room.equipment());
        return new RoomAvailability(room, room.capacity() < requirements.attendees(), missing, conflicts);
    }

    public boolean available() {
        return !insufficientCapacity && missingEquipment.isEmpty() && conflicts.isEmpty();
    }
}
